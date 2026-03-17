// ABOUTME: Entry point for the DeltaFi Go Hello World plugin.
// ABOUTME: Configures plugin coordinates and delegates lifecycle to the action kit.

package main

import (
	"log"

	actionkit "gitlab.com/deltafi/deltafi/deltafi-go-action-kit"
	_ "gitlab.com/deltafi/deltafi/deltafi-go-hello-world/actions"
	_ "gitlab.com/deltafi/deltafi/deltafi-go-hello-world/suppliers"
)

var (
	version     = "0.0.0-dev"
	description = "Go Hello World reference plugin for DeltaFi"
)

func init() {
	actionkit.WatchArtifact("config.yaml", &actionkit.ArtifactDefault{
		Data:        []byte("config: []"),
		ContentType: "application/yaml",
		Description: "Go hello world example config.yaml",
	}, processConfig)
}

func processConfig(fetched *actionkit.FetchedArtifact, err error) {
	if err != nil {
		log.Printf("Failed to load config.yaml: %v", err)
		return
	}
	log.Printf("Loaded config.yaml (version=%s):\n%s", fetched.Manifest.Version, string(fetched.Data))
}

func main() {
	actionkit.RunPlugin(&actionkit.PluginConfig{
		Coordinates: &actionkit.PluginCoordinates{
			GroupID:    "org.deltafi",
			ArtifactID: "deltafi-go-hello-world",
			Version:    version,
		},
		Domain:      "org.deltafi.go-hello-world",
		Description: description,
	})
}
