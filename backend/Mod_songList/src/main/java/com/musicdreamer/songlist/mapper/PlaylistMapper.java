package com.musicdreamer.songlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.songlist.entity.Playlist;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PlaylistMapper extends BaseMapper<Playlist> {
}
