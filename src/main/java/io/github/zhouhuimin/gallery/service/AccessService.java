package io.github.zhouhuimin.gallery.service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.auth.Actor;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Set;

@Service @RequiredArgsConstructor
public class AccessService {
    private final SpaceMapper spaces; private final MemberMapper members; private final PictureMapper pictures;
    public Space space(long id) { var s=spaces.selectById(id);if(s==null) throw new BusinessException(ErrorCode.NOT_FOUND);return s; }
    public String role(Actor actor,Space space) {
        if("PUBLIC".equals(space.getType())) return actor==null?"GUEST":actor.admin()?"ADMIN":"EDITOR";
        if(actor==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var m=members.selectOne(Wrappers.<SpaceMember>lambdaQuery().eq(SpaceMember::getSpaceId,space.getId()).eq(SpaceMember::getUserId,actor.id()));
        if(m==null) throw new BusinessException(ErrorCode.FORBIDDEN);return m.getRole();
    }
    public Space readSpace(Actor actor,long id) { var s=space(id);role(actor,s);return s; }
    public Space writeSpace(Actor actor,long id) {
        if(actor==null) throw new BusinessException(ErrorCode.UNAUTHORIZED);
        var s=space(id);BusinessException.require(Set.of("OWNER","ADMIN","EDITOR").contains(role(actor,s)),ErrorCode.FORBIDDEN);return s;
    }
    public Space manageSpace(Actor actor,long id) {
        var s=space(id);BusinessException.require("TEAM".equals(s.getType()) && Set.of("OWNER","ADMIN").contains(role(actor,s)),ErrorCode.FORBIDDEN);return s;
    }
    public Picture readPicture(Actor actor,long id) {
        var p=pictures.selectById(id);if(p==null) throw new BusinessException(ErrorCode.NOT_FOUND);
        var s=readSpace(actor,p.getSpaceId());
        if("PUBLIC".equals(s.getType()) && !"APPROVED".equals(p.getReviewStatus()))
            BusinessException.require(actor!=null && (actor.admin() || p.getUploaderId()==actor.id()),ErrorCode.FORBIDDEN);
        return p;
    }
    public Picture writePicture(Actor actor,long id) {
        var p=readPicture(actor,id);var s=writeSpace(actor,p.getSpaceId());
        if("PUBLIC".equals(s.getType())) BusinessException.require(actor.admin() || p.getUploaderId()==actor.id(),ErrorCode.FORBIDDEN);
        return p;
    }
}
