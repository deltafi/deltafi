---
# ABOUTME: Documents REST endpoints for user, role, and permission management.
# ABOUTME: Covers CRUD operations for users and roles, plus permission listing and user info.
---

# Users and Roles REST API

Endpoints for managing users, roles, and permissions. See [Authentication](../authentication.md) for the permission model overview.

## Users

### List Users

**Endpoint:** `GET /api/v2/users`

**Permission:** `UserRead`

**Response:** Array of user objects

**Example:**

```bash
curl http://deltafi-host/api/v2/users
```

### Get User

**Endpoint:** `GET /api/v2/users/{id}`

**Permission:** `UserRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | User identifier |

**Response:** User object

### Create User

**Endpoint:** `POST /api/v2/users`

**Permission:** `UserCreate`

**Request Body:**

```json
{
  "name": "alice",
  "password": "secure-password",
  "dn": null,
  "roleIds": ["550e8400-e29b-41d4-a716-446655440000"]
}
```

**Response:** Created user object

### Update User

**Endpoint:** `PUT /api/v2/users/{id}`

**Permission:** `UserUpdate`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | User identifier |

**Request Body:** Partial update JSON (only include fields to change)

```json
{
  "name": "alice-updated",
  "roleIds": ["550e8400-e29b-41d4-a716-446655440000"]
}
```

**Response:** Updated user object

### Delete User

**Endpoint:** `DELETE /api/v2/users/{id}`

**Permission:** `UserDelete`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | User identifier |

**Response:** Deleted user object

::: warning
The admin user cannot be deleted. Attempting to do so returns `403 Forbidden`.
:::

### Export Users and Roles

Exports all users and roles as formatted JSON for backup or migration.

**Endpoint:** `GET /api/v2/users/exportable`

**Permission:** `UserRead`

**Response:** Pretty-printed JSON of all users and roles

### Import Users and Roles

Imports users and roles from a JSON export.

**Endpoint:** `POST /api/v2/users/importable`

**Permission:** `UserCreate`

**Request Body:** `UsersAndRoles` JSON (as produced by the export endpoint)

**Response:** `null` on success, or error message with status `422`

## Roles

### List Roles

**Endpoint:** `GET /api/v2/roles`

**Permission:** `RoleRead`

**Response:** Array of role objects

### Get Role

**Endpoint:** `GET /api/v2/roles/{id}`

**Permission:** `RoleRead`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Role identifier |

**Response:** Role object

### Create Role

**Endpoint:** `POST /api/v2/roles`

**Permission:** `RoleCreate`

**Request Body:**

```json
{
  "name": "Operator",
  "permissions": ["StatusView", "DeltaFileMetadataView", "FlowView"]
}
```

**Response:** Created role object

### Update Role

**Endpoint:** `PUT /api/v2/roles/{id}`

**Permission:** `RoleUpdate`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Role identifier |

**Request Body:** Partial update JSON

**Response:** Updated role object

### Delete Role

**Endpoint:** `DELETE /api/v2/roles/{id}`

**Permission:** `RoleDelete`

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | UUID | Role identifier |

**Response:** Deleted role object

::: warning
The Admin role cannot be deleted. Attempting to do so returns `403 Forbidden`.
:::

## Permissions

### List Permissions

Returns all available permissions in the system.

**Endpoint:** `GET /api/v2/permissions`

**Permission:** None (open endpoint)

**Response:** Array of permission objects with category, name, and description

**Example:**

```bash
curl http://deltafi-host/api/v2/permissions
```

## Current User

Returns information about the currently authenticated user.

**Endpoint:** `GET /api/v2/me`

**Permission:** None (open endpoint)

**Response:**

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "alice",
  "permissions": ["StatusView", "DeltaFileMetadataView", "FlowView"]
}
```
