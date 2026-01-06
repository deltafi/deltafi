# GUI

## Message of the Day

Administrators can configure a message that will be displayed to users when they visit the DeltaFi UI. This is useful for communicating maintenance windows, policy changes, or other announcements.

### Configuration

Set the `messageOfTheDay` system property via the System Properties page in the UI or through the API. The message supports Markdown formatting including **bold**, *italic*, links, and lists.

```bash
deltafi properties set messageOfTheDay "System maintenance scheduled for **Sunday 2am-4am UTC**"
```

To clear the message:
```bash
deltafi properties unset messageOfTheDay
```

### Behavior

- The message appears as a modal dialog when a user visits any page in the UI
- Users can dismiss the dialog by clicking the close button or clicking outside the dialog
- Once dismissed, the message won't appear again until the next calendar day (based on the user's local timezone)
- If the message content changes, it will be shown immediately to all users, even if they dismissed a previous message that day