package com.musicdreamer.songlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.songlist.entity.PlaylistSong;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlaylistSongMapper extends BaseMapper<PlaylistSong> {
}
