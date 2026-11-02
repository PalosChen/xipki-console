/**
 * Copyright (c) 2026 trustasia-com. All rights reserved.
 * License Apache License 2.0
 */
package org.xipki.mgmt.web.security;

import java.io.Serializable;
import java.util.Set;
import org.xipki.mgmt.web.core.*;

/**
 * @author palos
 */
public record RoleDefinition(
    String name,
    String displayName,
    String description,
    String scope,
    boolean builtin,
    boolean enabled,
    boolean deleted,
    long revision,
    Set<String> permissions,
    String migrationSource,
    boolean migrationGenerated)
    implements Serializable {
  public RoleDefinition(
      String name,
      String displayName,
      String description,
      String scope,
      boolean builtin,
      boolean enabled,
      boolean deleted,
      long revision,
      Set<String> permissions) {
    this(
        name,
        displayName,
        description,
        scope,
        builtin,
        enabled,
        deleted,
        revision,
        permissions,
        null,
        false);
  }

  public RoleDefinition {
    permissions = Set.copyOf(permissions);
  }

  public boolean active() {
    return enabled && !deleted;
  }

  public boolean needsProfiles() {
    return permissions.stream().anyMatch(PermissionCatalog.ISSUANCE::contains);
  }

  public boolean privileged() {
    return "SUPER_ADMIN".equals(name)
        || permissions.stream().anyMatch(p -> PermissionCatalog.get(p).privileged());
  }
}
