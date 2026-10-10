package io.github.zhouhuimin.gallery.auth;
import io.github.zhouhuimin.gallery.common.*;
import jakarta.servlet.http.HttpServletRequest;
public record Actor(long id,String username,String displayName,String role) {
    public boolean admin() { return "ADMIN".equals(role); }
    public static Actor optional(HttpServletRequest request) { return (Actor)request.getAttribute("actor"); }
    public static Actor require(HttpServletRequest request) { var actor=optional(request); if(actor==null) throw new BusinessException(ErrorCode.UNAUTHORIZED); return actor; }
}
