package org.xipki.mgmt.web.controller;

import org.xipki.mgmt.web.core.*;

import org.xipki.mgmt.web.dto.*;
import org.xipki.mgmt.web.security.*;
import java.util.*;

public class RoleController {
    private final RoleService service;
    private final RoleCatalog roles;
    public RoleController(RoleService service,RoleCatalog roles) { this.service=service; this.roles=roles; }

    public ApiResponse<List<Map<String,Object>>> list() { return ApiResponse.ok(service.list()); }

    public ApiResponse<Collection<PermissionCatalog.Permission>> permissions() { return ApiResponse.ok(PermissionCatalog.entries()); }

    public ApiResponse<RoleDefinition> create(RoleRequests.Create body) {
        return ApiResponse.ok(service.save(body.name,null,body.displayName,body.description,body.scope,true,body.permissions,Boolean.TRUE.equals(body.confirmed)));
    }

    public ApiResponse<RoleDefinition> update(String name,RoleRequests.Update body) {
        if(body.revision==null || body.revision<1 || body.enabled==null) throw new RoleValidationException("Missing role revision or state");
        return ApiResponse.ok(service.save(name,body.revision,body.displayName,body.description,roles.find(name).scope(),body.enabled,body.permissions,Boolean.TRUE.equals(body.confirmed)));
    }

    public ApiResponse<Void> delete(String name,long revision) {
        service.delete(name,revision); return ApiResponse.ok();
    }
}
