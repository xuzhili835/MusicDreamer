package com.musicdreamer.songlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.songlist.entity.Album;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlbumMapper extends BaseMapper<Album> {
}
