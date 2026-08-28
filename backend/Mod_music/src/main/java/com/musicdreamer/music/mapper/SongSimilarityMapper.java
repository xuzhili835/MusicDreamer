package com.musicdreamer.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.music.entity.SongSimilarity;
import com.musicdreamer.music.vo.SimItemVO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 歌曲相似度矩阵 Mapper：在线只读 Top-N，离线任务先清后写。 */
public interface SongSimilarityMapper extends BaseMapper<SongSimilarity> {

    /** 某首歌按得分倒序的相似歌曲 Top-N。 */
    @Select("SELECT sim_song_id AS simSongId, score FROM song_similarity "
            + "WHERE song_id = #{songId} ORDER BY score DESC LIMIT #{limit}")
    List<SimItemVO> selectTopBySong(@Param("songId") Long songId, @Param("limit") int limit);

    /** 全表清空（离线重算第一步，与批量插入同事务）。 */
    @Delete("DELETE FROM song_similarity")
    int deleteAll();

    /** 批量写入。 */
    @Insert("<script>INSERT INTO song_similarity (song_id, sim_song_id, score) VALUES "
            + "<foreach collection='list' item='item' separator=','>(#{item.songId}, #{item.simSongId}, #{item.score})</foreach> "
            + "</script>")
    int batchInsert(@Param("list") List<SongSimilarity> list);
}
