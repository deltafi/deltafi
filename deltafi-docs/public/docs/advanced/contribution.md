# Core Development and Contribution Guide

## Getting Started with a Merge Request

1. Fork our repository
1. Create a new branch for your changes
1. Make changes and commit them with a clear and concise commit message
1. Add a ChangeLog entry for your changes.  See the ChangeLog section of this guide for details
1. Push your changes to your fork
1. Create a pull request targeting the main branch or an appropriate release branch

## Creating a ChangeLog entry for your changes

The markdown `CHANGELOG.md` file at the root of the repository holds all the ChangeLog information
for past releases of DeltaFi.  Unreleased changes have a markdown file located at `CHANGELOG/unreleased/`.
The ChangeLog files in `CHANGELOG/unreleased` are compiled at release time into the resulting `CHANGELOG.md`.

The `changelog` script located at `utils/changelog` is a tool for automating the creation of ChangeLog files
for individual changes, as well as compiling unreleased changes into the main `CHANGELOG.md`.  The script can
be utilized in several different ways based on developer preference:

- Use the script by directly invoking it in the core repo
- Create a shell alias to invoke the script
- Copy the script to a directory in your path, renamed to `git-changelog`.  You will be able to invoke the script
  from any git repo by `git changelog`

The ChangeLog script can be invoked as follows:

- `changelog` (with no arguments) invoked anywhere in your repository will create a stub ChangeLog file at
  `CHANGELOG/unreleased/<BRANCHNAME>.md` (if the file does not yet exist) and return the path of the ChangeLog
  file
- `changelog -e` will create a ChangeLog file if it doesn't exist, and open the file in an editor.  If no
  editor is specified in the `EDITOR` environment variable, vim will be used as the default editor
- `changelog -r <version>` will compile all the unreleased ChangeLog files into the main `CHANGELOG.md`.  This
  is only used during the release process
