package io.github.zhouhuimin.gallery.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.zhouhuimin.gallery.model.AiJob;
import org.apache.ibatis.annotations.*;
public interface AiJobMapper extends BaseMapper<AiJob> {
    @Select("SELECT * FROM ai_job WHERE id=#{id} FOR UPDATE") AiJob lock(String id);
    @Update("UPDATE ai_job SET status=#{next},updated_at=UTC_TIMESTAMP(6) WHERE id=#{id} AND status=#{previous}")
    int transition(@Param("id") String id,@Param("previous") String previous,@Param("next") String next);
}
