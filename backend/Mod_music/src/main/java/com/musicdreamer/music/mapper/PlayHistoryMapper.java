package com.musicdreamer.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.music.entity.PlayHistory;
import com.musicdreamer.music.vo.PlayRecordVO;
import com.musicdreamer.music.vo.RecentVO;
import com.musicdreamer.music.vo.SongCardVO;
import com.musicdreamer.music.vo.UserSongCountVO;
import com.musicdreamer.music.vo.UserSongStatVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 播放历史 Mapper：明细走 BaseMapper，聚合查询走注解 SQL。 */
public interface PlayHistoryMapper extends BaseMapper<PlayHistory> {

    /** 最近播放：按歌曲分组取最大播放时间倒序（去重）。 */
    @Select("SELECT s.id AS songId, s.name, COALESCE(NULLIF(s.singer_name, ''), u.nickname, u.username) AS singer, "
            + "s.album, s.cover_url AS coverUrl, s.duration, MAX(p.played_at) AS lastPlayedAt "
            + "FROM play_history p JOIN song s ON s.id = p.song_id LEFT JOIN user u ON u.id = s.singer_id "
            + "WHERE p.user_id = #{userId} AND p.play_complete = 1 AND s.status = 2 "
            + "GROUP BY s.id, s.name, u.nickname, u.username, s.singer_name, s.album, s.cover_url, s.duration "
            + "ORDER BY lastPlayedAt DESC LIMIT #{limit}")
    List<RecentVO> selectRecent(@Param("userId") Long userId, @Param("limit") int limit);

    /** 播放明细分页（join 歌曲与歌手）。 */
    @Select("SELECT p.id, p.song_id AS songId, s.name AS songName, s.cover_url AS coverUrl, "
            + "COALESCE(NULLIF(s.singer_name, ''), u.nickname, u.username) AS singer, p.play_duration AS playDuration, "
            + "p.play_complete AS playComplete, p.device_type AS deviceType, p.played_at AS playedAt "
            + "FROM play_history p JOIN song s ON s.id = p.song_id LEFT JOIN user u ON u.id = s.singer_id "
            + "WHERE p.user_id = #{userId} ORDER BY p.played_at DESC LIMIT #{offset}, #{size}")
    List<PlayRecordVO> selectHistory(@Param("userId") Long userId,
                                     @Param("offset") long offset, @Param("size") int size);

    @Select("SELECT COUNT(*) FROM play_history WHERE user_id = #{userId}")
    long countHistory(@Param("userId") Long userId);

    /** 周榜（rise）：近 7 天按歌曲计数倒序，仅已发布。 */
    @Select("SELECT s.id, s.name, COALESCE(NULLIF(s.singer_name, ''), u.nickname, u.username) AS singer, s.singer_id AS singerId, "
            + "s.album, s.style, s.duration, s.cover_url AS coverUrl, COUNT(*) AS weekPlayCount "
            + "FROM play_history p JOIN song s ON s.id = p.song_id LEFT JOIN user u ON u.id = s.singer_id "
            + "WHERE s.status = 2 AND p.played_at >= DATE_SUB(NOW(), INTERVAL 7 DAY) "
            + "GROUP BY s.id, s.name, u.nickname, u.username, s.singer_name, s.singer_id, s.album, s.style, s.duration, s.cover_url "
            + "ORDER BY weekPlayCount DESC LIMIT #{limit}")
    List<SongCardVO> selectRise(@Param("limit") int limit);

    /** 用户近 90 天播放过的歌曲及最后播放时间（推荐权重用）。 */
    @Select("SELECT song_id AS songId, MAX(played_at) AS lastPlayedAt FROM play_history "
            + "WHERE user_id = #{userId} AND play_complete = 1 "
            + "AND played_at >= DATE_SUB(NOW(), INTERVAL 90 DAY) GROUP BY song_id")
    List<UserSongStatVO> selectUserPlays90(@Param("userId") Long userId);

    /** 全量（用户,歌曲）播放次数聚合（离线评分矩阵数据源）。 */
    @Select("SELECT user_id AS userId, song_id AS songId, COUNT(*) AS cnt FROM play_history "
            + "WHERE play_complete = 1 GROUP BY user_id, song_id")
    List<UserSongCountVO> selectPlayAggregates();
}
