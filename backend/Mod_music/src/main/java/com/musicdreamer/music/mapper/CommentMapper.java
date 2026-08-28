package com.musicdreamer.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.music.entity.Comment;
import com.musicdreamer.music.vo.CommentVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/** 评论 Mapper：两级结构查询 join user 拼昵称头像。 */
public interface CommentMapper extends BaseMapper<Comment> {

    String CMT_COLS = "c.id, c.song_id AS songId, c.user_id AS userId, "
            + "COALESCE(u.nickname, u.username) AS nickname, u.avatar AS avatar, "
            + "c.parent_id AS parentId, c.content, c.like_count AS likeCount, c.create_time AS createTime";

    /** 顶层评论分页：最新。 */
    @Select("SELECT " + CMT_COLS + " FROM comment c LEFT JOIN user u ON u.id = c.user_id "
            + "WHERE c.song_id = #{songId} AND c.status = 1 AND c.parent_id IS NULL "
            + "ORDER BY c.create_time DESC LIMIT #{offset}, #{size}")
    List<CommentVO> selectTopNew(@Param("songId") Long songId,
                                 @Param("offset") long offset, @Param("size") int size);

    /** 顶层评论分页：最热（点赞数倒序）。 */
    @Select("SELECT " + CMT_COLS + " FROM comment c LEFT JOIN user u ON u.id = c.user_id "
            + "WHERE c.song_id = #{songId} AND c.status = 1 AND c.parent_id IS NULL "
            + "ORDER BY c.like_count DESC, c.create_time DESC LIMIT #{offset}, #{size}")
    List<CommentVO> selectTopHot(@Param("songId") Long songId,
                                 @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM comment WHERE song_id = #{songId} AND status = 1 AND parent_id IS NULL")
    long countTop(@Param("songId") Long songId);

    /** 子评论：按父评论集合查（时间正序）。 */
    @Select("<script>SELECT " + CMT_COLS + " FROM comment c LEFT JOIN user u ON u.id = c.user_id "
            + "WHERE c.status = 1 AND c.parent_id IN "
            + "<foreach collection='parentIds' item='item' open='(' separator=',' close=')'>#{item}</foreach> "
            + "ORDER BY c.create_time ASC</script>")
    List<CommentVO> selectChildren(@Param("parentIds") List<Long> parentIds);

    @Update("UPDATE comment SET like_count = like_count + 1 WHERE id = #{id}")
    int incrementLike(@Param("id") Long id);

    @Update("UPDATE comment SET like_count = GREATEST(like_count - 1, 0) WHERE id = #{id}")
    int decrementLike(@Param("id") Long id);

    /** 级联逻辑删子评论。 */
    @Update("UPDATE comment SET status = 0 WHERE parent_id = #{parentId} AND status = 1")
    int deleteChildren(@Param("parentId") Long parentId);
}
