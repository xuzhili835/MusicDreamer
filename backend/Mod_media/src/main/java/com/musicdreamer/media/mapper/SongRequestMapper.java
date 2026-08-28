package com.musicdreamer.media.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.media.entity.SongRequest;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SongRequestMapper extends BaseMapper<SongRequest> {
}
