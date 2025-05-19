/*
 *    DeltaFi - Data transformation and enrichment platform
 *
 *    Copyright 2021-2025 DeltaFi Contributors <deltafi@deltafi.org>
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
package styles

import (
	"strings"

	"github.com/charmbracelet/lipgloss"
)

var (
	// Base colors (Latte for Light, Mocha for Dark)
	Base   = lipgloss.AdaptiveColor{Light: "#eff1f5", Dark: "#000000"} // Base
	Mantle = lipgloss.AdaptiveColor{Light: "#e6e9ef", Dark: "#181825"} // Mantle
	Crust  = lipgloss.AdaptiveColor{Light: "#dce0e8", Dark: "#11111b"} // Crust

	// Text colors
	Text     = lipgloss.AdaptiveColor{Light: "#4c4f69", Dark: "#cdd6f4"} // Text
	Subtext0 = lipgloss.AdaptiveColor{Light: "#6c6f85", Dark: "#a6adc8"} // Subtext0
	Subtext1 = lipgloss.AdaptiveColor{Light: "#5c5f77", Dark: "#bac2de"} // Subtext1

	// Overlay colors
	Surface0 = lipgloss.AdaptiveColor{Light: "#ccd0da", Dark: "#313244"} // Surface0
	Surface1 = lipgloss.AdaptiveColor{Light: "#bcc0cc", Dark: "#45475a"} // Surface1
	Surface2 = lipgloss.AdaptiveColor{Light: "#acb0be", Dark: "#585b70"} // Surface2

	// Accent colors
	Rosewater  = lipgloss.AdaptiveColor{Light: "#dc8a78", Dark: "#f5e0dc"} // Rosewater
	Flamingo   = lipgloss.AdaptiveColor{Light: "#dd7878", Dark: "#f2cdcd"} // Flamingo
	Pink       = lipgloss.AdaptiveColor{Light: "#ea76cb", Dark: "#f5c2e7"} // Pink
	Mauve      = lipgloss.AdaptiveColor{Light: "#8839ef", Dark: "#cba6f7"} // Mauve
	Red        = lipgloss.AdaptiveColor{Light: "#d20f39", Dark: "#f38ba8"} // Red
	Maroon     = lipgloss.AdaptiveColor{Light: "#e64553", Dark: "#eba0ac"} // Maroon
	Peach      = lipgloss.AdaptiveColor{Light: "#fe640b", Dark: "#fab387"} // Peach
	Yellow     = lipgloss.AdaptiveColor{Light: "#df8e1d", Dark: "#f9e2af"} // Yellow
	Green      = lipgloss.AdaptiveColor{Light: "#40a02b", Dark: "#40a02b"} // Green
	Teal       = lipgloss.AdaptiveColor{Light: "#179299", Dark: "#94e2d5"} // Teal
	Sky        = lipgloss.AdaptiveColor{Light: "#04a5e5", Dark: "#89dceb"} // Sky
	Sapphire   = lipgloss.AdaptiveColor{Light: "#209fb5", Dark: "#74c7ec"} // Sapphire
	Blue       = lipgloss.AdaptiveColor{Light: "#1e66f5", Dark: "#1e66f5"} // Blue
	Lavender   = lipgloss.AdaptiveColor{Light: "#7287fd", Dark: "#b4befe"} // Lavender
	LightBlue  = lipgloss.AdaptiveColor{Light: "#1e66f5", Dark: "#89b4fa"} // Blue
	LightGreen = lipgloss.AdaptiveColor{Light: "#40a02b", Dark: "#a6e3a1"} // Green
	Gray       = lipgloss.AdaptiveColor{Light: "#7e8294", Dark: "#9299ba"} // Gray
	DarkGray   = lipgloss.AdaptiveColor{Light: "#565f89", Dark: "#565f89"} // DarkGray
)

// UI Component styles
var (
	BaseStyle = lipgloss.NewStyle().
			Foreground(Text).
			Background(Base)

	CenteredStyle = lipgloss.NewStyle().
			AlignHorizontal(lipgloss.Center)

	RightAlignedStyle = lipgloss.NewStyle().
				AlignHorizontal(lipgloss.Right)

	HeaderStyle = lipgloss.NewStyle().
			Foreground(Blue).
			Bold(true)

	SubheaderStyle = lipgloss.NewStyle().
			Foreground(LightBlue).
			Bold(true)

	ErrorStyle = lipgloss.NewStyle().
			Foreground(Red).
			Bold(true)

	SuccessStyle = lipgloss.NewStyle().
			Foreground(Green).
			Bold(true)

	WarningStyle = lipgloss.NewStyle().
			Foreground(Peach).
			Bold(true)

	InfoStyle = lipgloss.NewStyle().
			Foreground(Sky).
			Bold(true)

	CommentStyle = lipgloss.NewStyle().
			Foreground(Subtext0).
			Italic(true)

	ItalicStyle = lipgloss.NewStyle().
			Italic(true)
	AccentStyle = lipgloss.NewStyle().
			Foreground(Lavender)

	LinkStyle = lipgloss.NewStyle().
			Foreground(Sapphire).
			Underline(true)

	InputStyle = lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(Surface2).
			Padding(0, 1)

	SelectionStyle = lipgloss.NewStyle().
			Background(Surface0).
			Foreground(Text)
)

// Menu styles
var (
	MenuStyle = lipgloss.NewStyle().
			Background(Surface0).
			Padding(1)

	MenuItemStyle = lipgloss.NewStyle().
			Foreground(Text)

	MenuMarkerStyle = lipgloss.NewStyle().
			Foreground(DarkGray).
			Bold(true)

	SelectedMenuItemStyle = lipgloss.NewStyle().
				Background(Surface0).
				Foreground(LightBlue).
				Bold(true)
)

// StyleBuilder provides a fluent interface for building styles
type StyleBuilder struct {
	style lipgloss.Style
}

func NewStyleBuilder() *StyleBuilder {
	return &StyleBuilder{
		style: BaseStyle,
	}
}

func (sb *StyleBuilder) WithForeground(color lipgloss.AdaptiveColor) *StyleBuilder {
	sb.style = sb.style.Copy().Foreground(color)
	return sb
}

func (sb *StyleBuilder) WithBackground(color lipgloss.AdaptiveColor) *StyleBuilder {
	sb.style = sb.style.Copy().Background(color)
	return sb
}

func (sb *StyleBuilder) Bold() *StyleBuilder {
	sb.style = sb.style.Copy().Bold(true)
	return sb
}

func (sb *StyleBuilder) Italic() *StyleBuilder {
	sb.style = sb.style.Copy().Italic(true)
	return sb
}

func (sb *StyleBuilder) Underline() *StyleBuilder {
	sb.style = sb.style.Copy().Underline(true)
	return sb
}

func (sb *StyleBuilder) Border() *StyleBuilder {
	sb.style = sb.style.Copy().
		BorderStyle(lipgloss.RoundedBorder()).
		BorderForeground(Surface2)
	return sb
}

func (sb *StyleBuilder) Padding(n int) *StyleBuilder {
	sb.style = sb.style.Copy().Padding(n)
	return sb
}

func (sb *StyleBuilder) Build() lipgloss.Style {
	return sb.style
}

// Helper functions for common UI patterns
func CreateTab(label string, active bool) string {
	style := lipgloss.NewStyle().
		Padding(0, 2)

	if active {
		style = style.
			Foreground(Mauve).
			Bold(true).
			Border(lipgloss.NormalBorder()).
			BorderForeground(Mauve)
	} else {
		style = style.
			Foreground(Subtext0)
	}

	return style.Render(label)
}

func CreateMenuItem(label string, selected bool) string {
	if selected {
		return SelectedMenuItemStyle.Render(label)
	}
	return MenuItemStyle.Render(label)
}

func CreateProgressBar(width int, percentage float64, color lipgloss.AdaptiveColor) string {
	filled := int(float64(width) * percentage)
	empty := width - filled

	bar := lipgloss.NewStyle().Foreground(color).Render(strings.Repeat("█", filled))
	bar += lipgloss.NewStyle().Foreground(Surface0).Render(strings.Repeat("█", empty))

	return bar
}

func CreateBorderedBox(content string) string {
	return lipgloss.NewStyle().
		Border(lipgloss.RoundedBorder()).
		BorderForeground(Surface2).
		Padding(1).
		Render(content)
}

// ColorStyle is an alias for lipgloss.Style to make it more semantic
type ColorStyle = lipgloss.Style

// AppStyles contains all the styles used in the application
type AppStyles struct {
	Title   ColorStyle
	Normal  ColorStyle
	Success ColorStyle
	Warning ColorStyle
	Error   ColorStyle
	Help    ColorStyle
	Message ColorStyle
	Input   ColorStyle
	Loading ColorStyle
	Spinner ColorStyle
}

// DefaultStyles returns the default style set
func DefaultStyles() *AppStyles {
	return &AppStyles{
		Title: lipgloss.NewStyle().
			Bold(true).
			Foreground(lipgloss.Color("#7e22ce")).
			MarginLeft(2),

		Normal: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#e5e7eb")),

		Success: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#16a34a")),

		Warning: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#eab308")),

		Error: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#dc2626")),

		Help: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#6b7280")).
			Italic(true),

		Message: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#e5e7eb")).
			MarginLeft(4),

		Input: lipgloss.NewStyle().
			BorderStyle(lipgloss.RoundedBorder()).
			BorderForeground(lipgloss.Color("#005fff")).
			Padding(1),

		Loading: lipgloss.NewStyle().
			Foreground(lipgloss.Color("#8b5cf6")),

		Spinner: lipgloss.NewStyle().
			Foreground(lipgloss.Color("205")),
	}
}

// Common border styles
var (
	BorderStyle = lipgloss.RoundedBorder()

	DocStyle = lipgloss.NewStyle().
			Padding(1).
			Border(BorderStyle)
)

// Common layout helpers
var (
	Subtle    = lipgloss.AdaptiveColor{Light: "#D9DCCF", Dark: "#383838"}
	Highlight = lipgloss.AdaptiveColor{Light: "#874BFD", Dark: "#7D56F4"}

	DialogBoxStyle = lipgloss.NewStyle().
			Border(BorderStyle).
			BorderForeground(Highlight).
			Padding(1).
			PaddingLeft(4).
			PaddingRight(4).
			BorderTop(true).
			BorderLeft(true).
			BorderRight(true).
			BorderBottom(true)
)

// Helper functions for consistent spacing and alignment
func CenterText(text string, width int) string {
	return lipgloss.PlaceHorizontal(width, lipgloss.Center, text)
}

func PadText(text string, padding int) string {
	return lipgloss.NewStyle().
		PaddingLeft(padding).
		PaddingRight(padding).
		Render(text)
}
