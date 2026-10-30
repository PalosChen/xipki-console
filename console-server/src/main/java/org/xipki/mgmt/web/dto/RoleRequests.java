package org.xipki.mgmt.web.dto;

import org.xipki.mgmt.web.core.*;

import java.util.List;

public final class RoleRequests {
    public static class Fields extends StrictRequest {
        public String displayName;
        public String description;
        public List<String> permissions;
        public Boolean confirmed;
    }
    public static class Create extends Fields {
        public String name;
        public String scope;
    }
    public static class Update extends Fields {
        public Long revision;
        public Boolean enabled;
    }
    private RoleRequests() { }
}
