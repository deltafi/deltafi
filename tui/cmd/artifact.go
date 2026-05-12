/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2026 DeltaFi Contributors <deltafi@deltafi.org>
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package cmd

import (
	"fmt"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	"github.com/deltafi/tui/graphql"
	"github.com/deltafi/tui/internal/api"
	"github.com/deltafi/tui/internal/app"
	"github.com/deltafi/tui/internal/ui/styles"
	"github.com/spf13/cobra"
)

// ── Root ─────────────────────────────────────────────────────────────────────

var artifactsCmd = &cobra.Command{
	Use:     "artifacts",
	Short:   "Manage the artifact registry",
	GroupID: "deltafi",
	Long: `Manage versioned binary artifacts stored in the DeltaFi artifact registry.

Artifacts are scoped to a plugin (--owner <plugin-name>) or shared across the
organization (--owner shared).

Examples:
  deltafi artifacts list --owner my-plugin
  deltafi artifacts publish --owner my-plugin ./mylib-2.1.0.jar
  deltafi artifacts publish --owner my-plugin ./mylib-2.1.0.jar --name mylib --version 2.1.0
  deltafi artifacts download --owner shared models active -o ./models.bin
  deltafi artifacts purge-config set --owner my-plugin mylib --keep-latest 3`,
	RunE: func(cmd *cobra.Command, args []string) error {
		return cmd.Help()
	},
}

// ── publish ───────────────────────────────────────────────────────────────────

var artifactsPublishCmd = &cobra.Command{
	Use:   "publish <file>",
	Short: "Publish a new artifact version",
	Long: `Publish a new artifact version to the registry.

The artifact name defaults to the filename without its extension.
If --version is omitted the server generates a timestamp-based version.
Pass --no-active to skip updating the 'active' alias.
Labels are provided as key=value pairs: --label env=prod --label team=infra`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}

		filePath := args[0]

		// Default artifact name to filename
		artifactName, _ := cmd.Flags().GetString("name")
		if artifactName == "" {
			artifactName = stemName(filePath)
		}

		version, _ := cmd.Flags().GetString("version")
		description, _ := cmd.Flags().GetString("description")
		noActive, _ := cmd.Flags().GetBool("no-active")
		contentType, _ := cmd.Flags().GetString("content-type")
		labelStrs, _ := cmd.Flags().GetStringArray("label")
		labels := parseArtifactLabels(labelStrs)

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		resp, err := client.PublishArtifact(
			owner, artifactName, version, filePath,
			contentType, description, !noActive, labels)
		if err != nil {
			return wrapInError("Failed to publish artifact", err)
		}

		fmt.Println(styles.OK(fmt.Sprintf("Published %s @ %s", resp.ArtifactName, resp.Version)))
		if resp.IsActive {
			fmt.Println(styles.InfoStyle.Render("  → tagged as 'active'"))
		}
		fmt.Printf("  sha256:  %s\n", resp.Sha256)
		return nil
	},
}

// ── list ──────────────────────────────────────────────────────────────────────

var artifactsListCmd = &cobra.Command{
	Use:   "list",
	Short: "List artifacts for an owner",
	Long: `List all artifacts with their active version summary.

Use --owner to specify the plugin name, or --owner shared for organization-wide artifacts.
Use --all-owners to list artifacts across every owner in a single view.

  deltafi artifacts list --owner my-plugin
  deltafi artifacts list --owner shared
  deltafi artifacts list --all-owners
  deltafi artifacts list --all-owners --plain`,
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		allOwners, _ := cmd.Flags().GetBool("all-owners")
		plain, _ := cmd.Flags().GetBool("plain")
		showType, _ := cmd.Flags().GetBool("show-type")

		// --owner is a persistent flag on the parent; MarkFlagsMutuallyExclusive can't see it.
		if owner != "" && allOwners {
			return fmt.Errorf("--owner and --all-owners are mutually exclusive")
		}
		if owner == "" && !allOwners {
			return fmt.Errorf("specify --owner <name> or use --all-owners")
		}

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		if allOwners {
			responses, err := client.ListAllArtifacts()
			if err != nil {
				return wrapInError("Failed to list artifacts", err)
			}
			if len(responses) == 0 {
				fmt.Println(styles.InfoStyle.Render("No artifacts found."))
				return nil
			}
			if plain {
				// Flat table with Owner column prepended
				rows := [][]string{}
				for _, resp := range responses {
					for _, a := range resp.Artifacts {
						rows = append(rows, artifactListRow(a, showType, resp.PluginName))
					}
				}
				t := api.NewTable(artifactListHeaders(showType, true), rows)
				renderAsSimpleTable(t, true)
			} else {
				// Per-owner tables
				for _, resp := range responses {
					fmt.Println(styles.InfoStyle.Render(resp.PluginName))
					rows := [][]string{}
					for _, a := range resp.Artifacts {
						rows = append(rows, artifactListRow(a, showType, ""))
					}
					t := api.NewTable(artifactListHeaders(showType, false), rows)
					renderAsSimpleTable(t, false)
				}
			}
			return nil
		}

		resp, err := client.ListArtifacts(owner)
		if err != nil {
			return wrapInError("Failed to list artifacts", err)
		}

		if len(resp.Artifacts) == 0 {
			fmt.Println(styles.InfoStyle.Render("No artifacts found for owner: " + owner))
			return nil
		}

		rows := [][]string{}
		for _, a := range resp.Artifacts {
			rows = append(rows, artifactListRow(a, showType, ""))
		}

		t := api.NewTable(artifactListHeaders(showType, false), rows)
		renderAsSimpleTable(t, plain)
		return nil
	},
}

// artifactListHeaders builds the header row for the list command, optionally
// prepending an Owner column and/or inserting a Content-Type column.
func artifactListHeaders(showType, withOwner bool) []string {
	headers := make([]string, 0, 6)
	if withOwner {
		headers = append(headers, "Owner")
	}
	headers = append(headers, "Artifact", "Active Version", "Versions")
	if showType {
		headers = append(headers, "Content-Type")
	}
	headers = append(headers, "Description")
	return headers
}

// artifactListRow builds a row for the list command matching artifactListHeaders.
// Pass owner="" to omit the leading Owner cell.
func artifactListRow(a api.ArtifactSummary, showType bool, owner string) []string {
	active := a.ActiveVersion
	if active == "" {
		active = "(none)"
	}
	description := ""
	contentType := ""
	if a.ActiveManifest != nil {
		description = a.ActiveManifest.Description
		contentType = a.ActiveManifest.ContentType
	}
	row := make([]string, 0, 6)
	if owner != "" {
		row = append(row, owner)
	}
	row = append(row, a.ArtifactName, active, fmt.Sprintf("%d", a.TotalVersions))
	if showType {
		row = append(row, contentType)
	}
	row = append(row, description)
	return row
}

// ── versions ──────────────────────────────────────────────────────────────────

var artifactsVersionsCmd = &cobra.Command{
	Use:   "versions <artifactName>",
	Short: "List all versions of an artifact",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		plain, _ := cmd.Flags().GetBool("plain")
		artifactName := args[0]

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		resp, err := client.ListArtifactVersions(owner, artifactName)
		if err != nil {
			return wrapInError("Failed to list versions", err)
		}

		if len(resp.Versions) == 0 {
			fmt.Println(styles.InfoStyle.Render("No versions found for artifact: " + artifactName))
			return nil
		}

		versionAliases := resp.Aliases

		rows := [][]string{}
		for _, m := range resp.Versions {
			active := ""
			if m.Version == resp.ActiveVersion {
				active = "✓"
			}
			created := ""
			if m.CreatedAt != nil {
				created = m.CreatedAt.Local().Format("2006-01-02 15:04:05")
			}
			sort.Strings(versionAliases[m.Version])
			rows = append(rows, []string{
				m.Version,
				active,
				strings.Join(versionAliases[m.Version], ", "),
				created,
				formatArtifactSize(m.Size),
				m.ContentType,
				m.Description,
			})
		}

		t := api.NewTable(
			[]string{"Version", "Active", "Aliases", "Created", "Size", "Content-Type", "Description"},
			rows,
		)
		renderAsSimpleTable(t, plain)
		return nil
	},
}

// ── download ──────────────────────────────────────────────────────────────────

var artifactsDownloadCmd = &cobra.Command{
	Use:   "download <artifactName> <version>",
	Short: "Download an artifact version",
	Long: `Download an artifact version to a local file.

Use 'active' as the version to resolve the current active alias.
The default output filename is the artifact name; use -o to override.`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]
		version := args[1]
		outputPath, _ := cmd.Flags().GetString("output")

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		body, suggestedFilename, err := client.DownloadArtifact(owner, artifactName, version)
		if err != nil {
			return wrapInError("Failed to download artifact", err)
		}
		defer func() { _ = body.Close() }()

		filename := outputPath
		if filename == "" {
			// Strip any path components from server-supplied names (RFC 6266 recommendation).
			filename = filepath.Base(suggestedFilename)
			if filename == "" || filename == "." {
				filename = filepath.Base(artifactName)
			}
		}

		outFile, err := os.Create(filename)
		if err != nil {
			return wrapInError(
				fmt.Sprintf("Cannot write local output file %q", filename),
				fmt.Errorf("%w\n\nEnsure the output directory exists and is writable, or specify an explicit path with --output", err),
			)
		}
		defer func() { _ = outFile.Close() }()

		n, err := io.Copy(outFile, body)
		if err != nil {
			return wrapInError("Failed to write artifact", err)
		}

		fmt.Println(styles.OK(
			fmt.Sprintf("Downloaded %s @ %s → %s (%s)", artifactName, version, filename, formatArtifactSize(n)),
		))
		return nil
	},
}

// ── rollback ──────────────────────────────────────────────────────────────────

var artifactsRollbackCmd = &cobra.Command{
	Use:   "rollback <artifactName> <version>",
	Short: "Roll back the 'active' alias to a prior version",
	Long: `Move the 'active' alias of an artifact to any previously published version.

This is useful when a newly published version has an issue and you want to
revert consumers to an older build without deleting the new version.`,
	Args: cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]
		version := args[1]

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		resp, err := client.RollbackArtifact(owner, artifactName, version)
		if err != nil {
			return wrapInError("Failed to rollback artifact", err)
		}

		fmt.Println(styles.OK(
			fmt.Sprintf("Rolled back %s/%s: 'active' now points to %s",
				owner, resp.ArtifactName, resp.RolledBackTo),
		))
		return nil
	},
}

// ── delete ────────────────────────────────────────────────────────────────────

var artifactsDeleteCmd = &cobra.Command{
	Use:   "delete <artifactName> [version]",
	Short: "Delete an artifact version or entire artifact",
	Long: `Delete an artifact from the registry.

If a version is specified, only that version (blob + manifest) is deleted.
The version currently tagged as 'active' cannot be deleted; roll back to
another version first.

If no version is specified, the entire artifact is deleted (all versions,
aliases, and configuration).`,
	Args: cobra.RangeArgs(1, 2),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]
		force, _ := cmd.Flags().GetBool("force")

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		if len(args) == 2 {
			version := args[1]
			if !force {
				prompt := fmt.Sprintf("Delete %s/%s @ %s?", owner, artifactName, version)
				if !ConfirmPrompt(prompt) {
					fmt.Println("Aborted.")
					return nil
				}
			}
			if err := client.DeleteArtifactVersion(owner, artifactName, version); err != nil {
				return wrapInError("Failed to delete artifact version", err)
			}
			fmt.Println(styles.OK(fmt.Sprintf("Deleted %s/%s @ %s", owner, artifactName, version)))
		} else {
			if !force {
				prompt := fmt.Sprintf("Delete ALL versions of %s/%s?", owner, artifactName)
				if !ConfirmPrompt(prompt) {
					fmt.Println("Aborted.")
					return nil
				}
			}
			if err := client.DeleteArtifact(owner, artifactName); err != nil {
				return wrapInError("Failed to delete artifact", err)
			}
			fmt.Println(styles.OK(fmt.Sprintf("Deleted all versions of %s/%s", owner, artifactName)))
		}
		return nil
	},
}

// ── set-alias ─────────────────────────────────────────────────────────────────

var artifactsSetAliasCmd = &cobra.Command{
	Use:   "set-alias <artifactName> <alias> <version>",
	Short: "Set an alias to point to a specific version",
	Long: `Set any alias of an artifact to point to a specific version.

This is a generalization of rollback — you can set any alias (not just 'active')
to any previously published version.

Example:
  deltafi artifacts set-alias --owner my-plugin rules active 20240101.120000.000`,
	Args: cobra.ExactArgs(3),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]
		alias := args[1]
		version := args[2]

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		resp, err := client.SetAlias(owner, artifactName, alias, version)
		if err != nil {
			return wrapInError("Failed to set alias", err)
		}

		fmt.Println(styles.OK(
			fmt.Sprintf("Set '%s' → %s for %s/%s", resp.Alias, resp.Version, owner, resp.ArtifactName),
		))
		return nil
	},
}

// ── remove-alias ──────────────────────────────────────────────────────────────

var artifactsRemoveAliasCmd = &cobra.Command{
	Use:   "remove-alias <artifactName> <alias>",
	Short: "Remove an alias from an artifact",
	Args:  cobra.ExactArgs(2),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]
		alias := args[1]

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		if err := client.DeleteAlias(owner, artifactName, alias); err != nil {
			return wrapInError("Failed to remove alias", err)
		}

		fmt.Println(styles.OK(fmt.Sprintf("Removed alias '%s' from %s/%s", alias, owner, artifactName)))
		return nil
	},
}

// ── purge-config ──────────────────────────────────────────────────────────────

var artifactsPurgeConfigCmd = &cobra.Command{
	Use:   "purge-config",
	Short: "Get or set the purge policy for an artifact",
	RunE: func(cmd *cobra.Command, args []string) error {
		return cmd.Help()
	},
}

var artifactsPurgeConfigGetCmd = &cobra.Command{
	Use:   "get <artifactName>",
	Short: "Get the purge policy for an artifact",
	Args:  cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		config, err := client.GetPurgeConfig(owner, artifactName)
		if err != nil {
			return wrapInError("Failed to get purge config", err)
		}

		fmt.Printf("Purge policy for %s/%s:\n", owner, artifactName)
		fmt.Printf("  keep-latest:     %d\n", config.KeepLatest)
		fmt.Printf("  max-age-minutes: %d  (%s)\n", config.MaxAgeMinutes, formatArtifactDuration(config.MaxAgeMinutes))
		return nil
	},
}

var artifactsPurgeConfigSetCmd = &cobra.Command{
	Use:   "set <artifactName>",
	Short: "Set the purge policy for an artifact",
	Long: `Set the purge retention policy for an artifact.

Versions are eligible for purge when they are:
  - Not the current 'active' version, AND
  - Outside the most-recent N versions (--keep-latest), AND
  - Older than --max-age-minutes

Set --max-age-minutes 0 to disable age-based purging.`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]
		keepLatest, _ := cmd.Flags().GetInt("keep-latest")
		maxAgeMinutes, _ := cmd.Flags().GetInt64("max-age-minutes")

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		config := api.PurgeConfig{
			KeepLatest:    keepLatest,
			MaxAgeMinutes: maxAgeMinutes,
		}
		if err := client.SetPurgeConfig(owner, artifactName, config); err != nil {
			return wrapInError("Failed to set purge config", err)
		}

		fmt.Println(styles.OK(fmt.Sprintf("Updated purge policy for %s/%s", owner, artifactName)))
		fmt.Printf("  keep-latest:     %d\n", keepLatest)
		fmt.Printf("  max-age-minutes: %d  (%s)\n", maxAgeMinutes, formatArtifactDuration(maxAgeMinutes))
		return nil
	},
}

// ── purge ─────────────────────────────────────────────────────────────────────

var artifactsPurgeCmd = &cobra.Command{
	Use:   "purge <artifactName>",
	Short: "Trigger an immediate purge for an artifact",
	Long: `Apply the purge policy immediately to an artifact, removing eligible versions.

The current 'active' version is always protected from purging.`,
	Args: cobra.ExactArgs(1),
	RunE: func(cmd *cobra.Command, args []string) error {
		RequireRunningDeltaFi()

		owner, _ := cmd.Flags().GetString("owner")
		if owner == "" {
			return fmt.Errorf("--owner is required (plugin name or 'shared')")
		}
		artifactName := args[0]

		client, err := app.GetInstance().GetAPIClient()
		if err != nil {
			return clientError(err)
		}

		resp, err := client.PurgeArtifact(owner, artifactName)
		if err != nil {
			return wrapInError("Failed to purge artifact", err)
		}

		if len(resp.PurgedVersions) == 0 {
			fmt.Println(styles.InfoStyle.Render("No versions were eligible for purge."))
		} else {
			fmt.Println(styles.OK(
				fmt.Sprintf("Purged %d version(s) of %s/%s:", len(resp.PurgedVersions), owner, artifactName),
			))
			for _, v := range resp.PurgedVersions {
				fmt.Printf("  - %s\n", v)
			}
		}
		return nil
	},
}

// ── helpers ───────────────────────────────────────────────────────────────────

// parseArtifactLabels converts ["key=value", ...] slices into a map.
func parseArtifactLabels(strs []string) map[string]string {
	if len(strs) == 0 {
		return nil
	}
	labels := make(map[string]string, len(strs))
	for _, s := range strs {
		parts := strings.SplitN(s, "=", 2)
		if len(parts) == 2 {
			labels[parts[0]] = parts[1]
		}
	}
	return labels
}

// formatArtifactSize converts a byte count into a human-readable string.
func formatArtifactSize(b int64) string {
	const unit = 1024
	if b < unit {
		return fmt.Sprintf("%d B", b)
	}
	div, exp := int64(unit), 0
	for n := b / unit; n >= unit; n /= unit {
		div *= unit
		exp++
	}
	return fmt.Sprintf("%.1f %cB", float64(b)/float64(div), "KMGTPE"[exp])
}

// stemName returns the filename with its extension.
// e.g. "/path/to/mylib-2.1.0.jar" → "mylib-2.1.0.jar"
func stemName(filePath string) string {
	base := filepath.Base(filePath)
	return base
}

// formatArtifactDuration converts a minute count into a human-readable string.
func formatArtifactDuration(minutes int64) string {
	if minutes == 0 {
		return "no age limit"
	}
	d := time.Duration(minutes) * time.Minute
	days := int(d.Hours()) / 24
	if days >= 1 {
		hours := int(d.Hours()) % 24
		if hours == 0 {
			return fmt.Sprintf("%d day(s)", days)
		}
		return fmt.Sprintf("%d day(s) %dh", days, hours)
	}
	return d.String()
}

// getOwnerList returns "shared" plus every installed plugin's owner string
// (groupId.artifactId), excluding the system-plugin placeholder.
func getOwnerList() ([]string, error) {
	owners := []string{"shared"}
	resp, err := graphql.GetPlugins()
	if err != nil {
		return nil, err
	}
	for _, plugin := range resp.Plugins {
		coords := plugin.PluginCoordinates
		if coords.ArtifactId == "" || coords.ArtifactId == "system-plugin" {
			continue
		}
		owners = append(owners, coords.GroupId+"."+coords.ArtifactId)
	}
	return owners, nil
}

// getArtifactList returns the artifact names registered under owner.
func getArtifactList(owner string) ([]string, error) {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return nil, err
	}
	resp, err := client.ListArtifacts(owner)
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(resp.Artifacts))
	for _, a := range resp.Artifacts {
		names = append(names, a.ArtifactName)
	}
	return names, nil
}

// getArtifactAliases returns the known aliases for a specific artifact.
func getArtifactAliases(owner, artifactName string) ([]string, error) {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return nil, err
	}
	resp, err := client.ListArtifacts(owner)
	if err != nil {
		return nil, err
	}
	for _, a := range resp.Artifacts {
		if a.ArtifactName == artifactName {
			aliases := make([]string, 0, len(a.Aliases))
			for alias := range a.Aliases {
				aliases = append(aliases, alias)
			}
			return aliases, nil
		}
	}
	return nil, nil
}

// getArtifactVersions returns the published versions for a specific artifact.
func getArtifactVersions(owner, artifactName string) ([]string, error) {
	client, err := app.GetInstance().GetAPIClient()
	if err != nil {
		return nil, err
	}
	resp, err := client.ListArtifactVersions(owner, artifactName)
	if err != nil {
		return nil, err
	}
	versions := make([]string, 0, len(resp.Versions))
	for _, m := range resp.Versions {
		versions = append(versions, m.Version)
	}
	return versions, nil
}

// ── completion functions ──────────────────────────────────────────────────────

// ownerFlag returns the --owner flag value, or "" if unset.
func ownerFlag(cmd *cobra.Command) string {
	s, _ := cmd.Flags().GetString("owner")
	return s
}

// completeArtifactOwners suggests valid --owner values.
func completeArtifactOwners(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	owners, err := getOwnerList()
	if err != nil {
		return nil, cobra.ShellCompDirectiveError
	}
	return escapedCompletions(owners, toComplete)
}

// completeArtifactName completes a single <artifactName> positional argument.
// Used by: versions, purge-config get/set, purge.
func completeArtifactName(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	if len(args) != 0 {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	owner := ownerFlag(cmd)
	if owner == "" {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	names, err := getArtifactList(owner)
	if err != nil {
		return nil, cobra.ShellCompDirectiveError
	}
	return escapedCompletions(names, toComplete)
}

// completeArtifactNameThenVersion returns a ValidArgsFunction for commands with
// the shape <artifactName> <version>.  Pass includeAliases=true to also suggest
// the artifact's named aliases alongside its versions (used by download).
func completeArtifactNameThenVersion(includeAliases bool) func(*cobra.Command, []string, string) ([]string, cobra.ShellCompDirective) {
	return func(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
		owner := ownerFlag(cmd)
		if owner == "" {
			return nil, cobra.ShellCompDirectiveNoFileComp
		}
		switch len(args) {
		case 0:
			names, err := getArtifactList(owner)
			if err != nil {
				return nil, cobra.ShellCompDirectiveError
			}
			return escapedCompletions(names, toComplete)
		case 1:
			versions, err := getArtifactVersions(owner, args[0])
			if err != nil {
				return nil, cobra.ShellCompDirectiveError
			}
			if includeAliases {
				aliases, err := getArtifactAliases(owner, args[0])
				if err != nil {
					return nil, cobra.ShellCompDirectiveError
				}
				versions = append(aliases, versions...)
			}
			return escapedCompletions(versions, toComplete)
		}
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
}

// completeArtifactNameThenAlias completes <artifactName> <alias>.
// Used by: remove-alias.
func completeArtifactNameThenAlias(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	owner := ownerFlag(cmd)
	if owner == "" {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	switch len(args) {
	case 0:
		names, err := getArtifactList(owner)
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(names, toComplete)
	case 1:
		aliases, err := getArtifactAliases(owner, args[0])
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(aliases, toComplete)
	}
	return nil, cobra.ShellCompDirectiveNoFileComp
}

// completeSetAlias completes the three-argument shape: <artifactName> <alias> <version>.
// Arg 1 (alias) suggests existing aliases so the user can autocomplete when updating one;
// a new alias name can still be typed freely.
func completeSetAlias(cmd *cobra.Command, args []string, toComplete string) ([]string, cobra.ShellCompDirective) {
	owner := ownerFlag(cmd)
	if owner == "" {
		return nil, cobra.ShellCompDirectiveNoFileComp
	}
	switch len(args) {
	case 0:
		names, err := getArtifactList(owner)
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(names, toComplete)
	case 1:
		aliases, err := getArtifactAliases(owner, args[0])
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(aliases, toComplete)
	case 2:
		versions, err := getArtifactVersions(owner, args[0])
		if err != nil {
			return nil, cobra.ShellCompDirectiveError
		}
		return escapedCompletions(versions, toComplete)
	}
	return nil, cobra.ShellCompDirectiveNoFileComp
}

// ── init ──────────────────────────────────────────────────────────────────────

func init() {
	// --owner is a persistent flag inherited by all subcommands
	artifactsCmd.PersistentFlags().String("owner", "", "Artifact owner: plugin name or 'shared'")

	// publish
	artifactsPublishCmd.Flags().String("name", "", "Artifact name (defaults to filename without extension)")
	artifactsPublishCmd.Flags().String("version", "", "Version string (server generates a timestamp version if omitted)")
	artifactsPublishCmd.Flags().String("description", "", "Human-readable description of this version")
	artifactsPublishCmd.Flags().Bool("no-active", false, "Do not update the 'active' alias after publishing")
	artifactsPublishCmd.Flags().String("content-type", "", "MIME content type (default: application/octet-stream)")
	artifactsPublishCmd.Flags().StringArray("label", nil, "Label in key=value format (may be repeated)")

	// list / versions
	artifactsListCmd.Flags().BoolP("plain", "p", false, "Plain output without table borders")
	artifactsListCmd.Flags().Bool("all-owners", false, "List artifacts across all owners")
	artifactsListCmd.Flags().Bool("show-type", false, "Include a Content-Type column")
	artifactsVersionsCmd.Flags().BoolP("plain", "p", false, "Plain output without table borders")

	// download
	artifactsDownloadCmd.Flags().StringP("output", "o", "", "Output file path (defaults to artifact name)")

	// delete
	artifactsDeleteCmd.Flags().BoolP("force", "f", false, "Skip confirmation prompt")

	// purge-config set
	artifactsPurgeConfigSetCmd.Flags().Int("keep-latest", 5,
		"Number of most-recent versions to always keep")
	artifactsPurgeConfigSetCmd.Flags().Int64("max-age-minutes", 43200,
		"Versions older than this (in minutes) may be purged; 0 = no age limit")

	// Wire the hierarchy
	artifactsPurgeConfigCmd.AddCommand(artifactsPurgeConfigGetCmd)
	artifactsPurgeConfigCmd.AddCommand(artifactsPurgeConfigSetCmd)

	artifactsCmd.AddCommand(artifactsPublishCmd)
	artifactsCmd.AddCommand(artifactsListCmd)
	artifactsCmd.AddCommand(artifactsVersionsCmd)
	artifactsCmd.AddCommand(artifactsDownloadCmd)
	artifactsCmd.AddCommand(artifactsRollbackCmd)
	artifactsCmd.AddCommand(artifactsDeleteCmd)
	artifactsCmd.AddCommand(artifactsSetAliasCmd)
	artifactsCmd.AddCommand(artifactsRemoveAliasCmd)
	artifactsCmd.AddCommand(artifactsPurgeConfigCmd)
	artifactsCmd.AddCommand(artifactsPurgeCmd)

	// --owner flag completion: "shared" + every installed plugin's artifact ID.
	// Registered on every subcommand (not just the parent) so Cobra can resolve
	// the completion function when the child command is active.
	for _, sub := range []*cobra.Command{
		artifactsCmd,
		artifactsPublishCmd,
		artifactsListCmd,
		artifactsVersionsCmd,
		artifactsDownloadCmd,
		artifactsRollbackCmd,
		artifactsDeleteCmd,
		artifactsSetAliasCmd,
		artifactsRemoveAliasCmd,
		artifactsPurgeConfigGetCmd,
		artifactsPurgeConfigSetCmd,
		artifactsPurgeCmd,
	} {
		_ = sub.RegisterFlagCompletionFunc("owner", completeArtifactOwners)
	}

	// Positional argument completion — each command gets a function matched to its
	// exact argument shape so completion is self-contained and rename-safe.
	artifactsVersionsCmd.ValidArgsFunction = completeArtifactName
	artifactsPurgeConfigGetCmd.ValidArgsFunction = completeArtifactName
	artifactsPurgeConfigSetCmd.ValidArgsFunction = completeArtifactName
	artifactsPurgeCmd.ValidArgsFunction = completeArtifactName

	artifactsDownloadCmd.ValidArgsFunction = completeArtifactNameThenVersion(true)
	artifactsRollbackCmd.ValidArgsFunction = completeArtifactNameThenVersion(false)
	artifactsDeleteCmd.ValidArgsFunction = completeArtifactNameThenVersion(false)

	artifactsRemoveAliasCmd.ValidArgsFunction = completeArtifactNameThenAlias
	artifactsSetAliasCmd.ValidArgsFunction = completeSetAlias

	rootCmd.AddCommand(artifactsCmd)
}
