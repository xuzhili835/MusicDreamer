package com.musicdreamer.music.mapper;

import com.musicdreamer.music.vo.UserSongPairVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 收藏表只读 Mapper（跨域读，课设简化为本地直查）：
 * 离线推荐任务用其给收藏过的（用户,歌曲）评分 +1。
 */
public interface CollectMapper {

    @Select("SELECT user_id AS userId, song_id AS songId FROM collect")
    List<UserSongPairVO> selectCollectedPairs();

    /** 删除歌曲时连带清理收藏（管理端删除歌曲用）。 */
    @Delete("DELETE FROM collect WHERE song_id = #{songId}")
    int deleteBySongId(@Param("songId") Long songId);
}
