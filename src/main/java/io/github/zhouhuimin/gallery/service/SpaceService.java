package io.github.zhouhuimin.gallery.service;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.zhouhuimin.gallery.auth.*;
import io.github.zhouhuimin.gallery.common.*;
import io.github.zhouhuimin.gallery.mapper.*;
import io.github.zhouhuimin.gallery.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service @RequiredArgsConstructor
public class SpaceService {
    private final SpaceMapper spaces;private final MemberMapper members;private final InviteMapper invites;private final AccessService access;
    private final AuthService auth;private final MaintenanceMapper maintenance;private final AiJobMapper jobs;private final UserMapper users;
    @Transactional public Space create(Actor actor,String name,String type) {
        users.lock(actor.id());
        BusinessException.require(Set.of("PRIVATE","TEAM").contains(type),ErrorCode.BAD_REQUEST);
        BusinessException.require(spaces.selectCount(Wrappers.<Space>lambdaQuery().eq(Space::getOwnerId,actor.id()))<10,ErrorCode.QUOTA_EXCEEDED);
        var s=new Space();s.setName(name.trim());s.setType(type);s.setOwnerId(actor.id());s.setMaxBytes(type.equals("PRIVATE")?104857600L:524288000L);
        s.setMaxCount(type.equals("PRIVATE")?1000:5000);s.setUsedBytes(0L);s.setUsedCount(0);spaces.insert(s);
        var m=new SpaceMember();m.setSpaceId(s.getId());m.setUserId(actor.id());m.setRole("OWNER");members.insert(m);
        maintenance.audit(actor.id(),s.getId(),"SPACE_CREATE",s.getId().toString());return s;
    }
    public Map<String,Object> invite(Actor actor,long spaceId,String role) {
        var s=access.manageSpace(actor,spaceId);String current=access.role(actor,s);
        BusinessException.require(Set.of("ADMIN","EDITOR","VIEWER").contains(role) && (!"ADMIN".equals(role)||"OWNER".equals(current)),ErrorCode.FORBIDDEN);
        var token=auth.randomToken();var invite=new SpaceInvite();invite.setSpaceId(spaceId);invite.setRole(role);invite.setCreatedBy(actor.id());
        invite.setTokenHash(DigestUtil.sha256Hex(token));invite.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusDays(2));invites.insert(invite);
        maintenance.audit(actor.id(),spaceId,"INVITE_CREATE",role);
        return Map.of("token",token,"expiresAt",invite.getExpiresAt(),"role",role);
    }
    @Transactional public Space accept(Actor actor,String token) {
        var invite=invites.lock(DigestUtil.sha256Hex(token));
        BusinessException.require(invite!=null && invite.getUsedBy()==null && invite.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)),ErrorCode.BAD_REQUEST);
        var s=access.space(invite.getSpaceId());
        var m=new SpaceMember();m.setSpaceId(s.getId());m.setUserId(actor.id());m.setRole(invite.getRole());members.insert(m);
        invite.setUsedBy(actor.id());invites.updateById(invite);maintenance.audit(actor.id(),s.getId(),"INVITE_ACCEPT",Long.toString(actor.id()));return s;
    }
    @Transactional public void member(Actor actor,long spaceId,long userId,String role,boolean remove) {
        var s=access.manageSpace(actor,spaceId);String actorRole=access.role(actor,s);
        var m=members.selectOne(Wrappers.<SpaceMember>lambdaQuery().eq(SpaceMember::getSpaceId,spaceId).eq(SpaceMember::getUserId,userId));
        BusinessException.require(m!=null,ErrorCode.NOT_FOUND);
        BusinessException.require(!"OWNER".equals(m.getRole()) && ("OWNER".equals(actorRole) || !"ADMIN".equals(m.getRole())),ErrorCode.FORBIDDEN);
        if(remove) members.deleteById(m);
        else { BusinessException.require(Set.of("ADMIN","EDITOR","VIEWER").contains(role) && (!"ADMIN".equals(role)||"OWNER".equals(actorRole)),ErrorCode.FORBIDDEN);m.setRole(role);members.updateById(m); }
        maintenance.audit(actor.id(),spaceId,remove?"MEMBER_REMOVE":"MEMBER_ROLE",Long.toString(userId));
    }
    @Transactional public void rename(Actor actor,long id,String name) {
        var s=access.space(id);BusinessException.require(actor!=null && s.getOwnerId()==actor.id() && !"PUBLIC".equals(s.getType()),ErrorCode.FORBIDDEN);
        s.setName(name.trim());spaces.updateById(s);maintenance.audit(actor.id(),id,"SPACE_RENAME",Long.toString(id));
    }
    @Transactional public void delete(Actor actor,long id) {
        var s=spaces.lock(id);BusinessException.require(s!=null,ErrorCode.NOT_FOUND);
        BusinessException.require(s.getOwnerId()==actor.id() && !"PUBLIC".equals(s.getType()),ErrorCode.FORBIDDEN);
        BusinessException.require(s.getUsedCount()==0 && jobs.selectCount(Wrappers.<AiJob>lambdaQuery().eq(AiJob::getSpaceId,id))==0,ErrorCode.CONFLICT);
        invites.delete(Wrappers.<SpaceInvite>lambdaQuery().eq(SpaceInvite::getSpaceId,id));members.delete(Wrappers.<SpaceMember>lambdaQuery().eq(SpaceMember::getSpaceId,id));
        maintenance.audit(actor.id(),id,"SPACE_DELETE",Long.toString(id));spaces.deleteById(id);
    }
}
