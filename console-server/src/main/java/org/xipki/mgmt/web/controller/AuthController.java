package org.xipki.mgmt.web.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.xipki.mgmt.web.audit.AuditFilter;
import org.xipki.mgmt.web.core.*;
import org.xipki.mgmt.web.dto.*;
import org.xipki.mgmt.web.security.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class AuthController {
    private final WebUserDetailsService users;
    private final Password passwords;
    private final SessionManager sessions;
    private final AccountService accounts;
    private final LoginThrottle throttle;
    private final String dummyHash;
    public AuthController(WebUserDetailsService users,Password passwords,SessionManager sessions,AccountService accounts,LoginThrottle throttle) {
        this.users=users;this.passwords=passwords;this.sessions=sessions;this.accounts=accounts;this.throttle=throttle;
        // Unknown usernames still incur one password verification.
        dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public Reply<ApiResponse<Map<String,Object>>> login(Map<String,String> credentials,HttpServletRequest request,HttpServletResponse response) {
        String username=credentials.get("username"),password=credentials.get("password");
        request.setAttribute(AuditFilter.ACTOR,username);response.setHeader("Cache-Control","no-store");
        if(username==null || username.length()>128 || password==null || password.getBytes(StandardCharsets.UTF_8).length>72)
            return invalid();
        throttle.reserve(username,request.getRemoteAddr());
        WebPrincipal user;
        try { user=users.loadUserByUsername(username); }
        catch(ApiException ex) {
            if(ex.status()!=401) throw ex;
            passwords.matches(password,dummyHash);return invalid();
        }
        if(!passwords.matches(password,user.getPassword()) || !user.isEnabled()) return invalid();
        user=accounts.upgradePasswordAfterLogin(user,password);
        sessions.login(request,response,user);throttle.success(username);accounts.recordLogin(user);
        return Reply.ok(ApiResponse.ok(userData(user)));
    }
    private static Reply<ApiResponse<Map<String,Object>>> invalid() { return Reply.status(401).body(ApiResponse.error("Invalid credentials")); }
    public ApiResponse<Void> password(IdentityRequests.Password body,HttpServletRequest req,HttpServletResponse resp) {
        resp.setHeader("Cache-Control","no-store");
        throttle.reserve("password:"+AccountService.principal().getUsername(),req.getRemoteAddr());
        accounts.changePassword(body.currentPassword,body.newPassword);sessions.logout(req,resp);return ApiResponse.ok();
    }
    public ApiResponse<Void> reauth(IdentityRequests.Reauth body,HttpServletRequest req,HttpServletResponse resp) {
        resp.setHeader("Cache-Control","no-store");String key="reauth:"+AccountService.principal().getUsername();
        throttle.reserve(key,req.getRemoteAddr());accounts.reauthenticate(body.password);throttle.success(key);
        sessions.confirm(req);return ApiResponse.ok();
    }
    public ApiResponse<Map<String,Object>> currentUser() {
        var user=AccountService.principal();var data=userData(user);data.put("displayName",accounts.currentDisplayName());return ApiResponse.ok(data);
    }
    public ApiResponse<Map<String,Object>> profile(IdentityRequests.Update body,HttpServletResponse resp) {
        resp.setHeader("Cache-Control","no-store");accounts.updateOwnProfile(body.displayName);
        return ApiResponse.ok(Collections.singletonMap("displayName",body.displayName));
    }
    public ApiResponse<Void> logout(HttpServletRequest req,HttpServletResponse resp) { sessions.logout(req,resp);return ApiResponse.ok(); }
    private static Map<String,Object> userData(WebPrincipal user) {
        var data=new LinkedHashMap<String,Object>();
        data.put("username",user.getUsername());data.put("id",user.id());data.put("displayName",user.displayName());
        data.put("passwordChangeRequired",user.passwordChangeRequired());data.put("bindings",user.bindings());
        data.put("roles",user.getAuthorities().stream().map(WebPrincipal.Authority::getAuthority).filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).distinct().sorted().toList());
        data.put("permissions",user.getAuthorities().stream().map(WebPrincipal.Authority::getAuthority).filter(a -> a.startsWith("PERM_")).map(a -> a.substring(5)).distinct().sorted().toList());
        var rolePermissions=new LinkedHashMap<String,Set<String>>();
        for(var binding:user.bindings()) rolePermissions.put(binding.role(),binding.definition().active()?binding.definition().permissions():Set.of());
        data.put("rolePermissions",rolePermissions);return data;
    }
}
