package com.musicdreamer.music.vo;

import lombok.Data;

/** 收藏表（user_id, song_id）只读查询结果。 */
@Data
public class UserSongPairVO {

    private Long userId;

    private Long songId;
}
