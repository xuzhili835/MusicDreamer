package com.musicdreamer.music.service;

import com.musicdreamer.common.api.ErrorCode;
import com.musicdreamer.common.auth.AuthContext;
import com.musicdreamer.common.exception.BizException;
import com.musicdreamer.music.dto.CommentAddDTO;
import com.musicdreamer.music.entity.Comment;
import com.musicdreamer.music.entity.Song;
import com.musicdreamer.music.mapper.CommentMapper;
import com.musicdreamer.music.mapper.SongMapper;
import com.musicdreamer.music.vo.CommentVO;
import com.musicdreamer.music.util.CacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 评论：两级结构、逻辑删、Redis Set 防重的点赞。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommentService {

    private static final String LIKE_KEY_PREFIX = "like:comment:";

    private final CommentMapper commentMapper;
    private final SongMapper songMapper;
    private final CacheHelper cache;
    private final StringRedisTemplate redis;

    /** 两级评论列表：顶层分页（new 时间倒序 / hot 点赞倒序），子评论时间正序。 */
    public Map<String, Object> list(Long songId, String sort, int page, int size) {
        if (page <= 0) page = 1;
        if (size <= 0) size = 20;
        boolean hot = "hot".equals(sort);
        long offset = (long) (page - 1) * size;

        List<CommentVO> tops = hot
                ? commentMapper.selectTopHot(songId, offset, size)
                : commentMapper.selectTopNew(songId, offset, size);
        long total = commentMapper.countTop(songId);

        if (!tops.isEmpty()) {
            List<Long> parentIds = tops.stream().map(CommentVO::getId).collect(Collectors.toList());
            Map<Long, List<CommentVO>> childrenByParent = commentMapper.selectChildren(parentIds).stream()
                    .collect(Collectors.groupingBy(CommentVO::getParentId));
            for (CommentVO top : tops) {
                top.setChildren(childrenByParent.getOrDefault(top.getId(), new ArrayList<>()));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", total);
        result.put("comments", tops);
        return result;
    }

    /** 发表评论：parentId 非空时校验并拍平到两级。 */
    public Long add(CommentAddDTO dto) {
        Long userId = AuthContext.requireLogin();
        Song song = songMapper.selectById(dto.getSongId());
        if (song == null) {
            throw new BizException(ErrorCode.SONG_NOT_FOUND);
        }

        Long parentId = dto.getParentId();
        if (parentId != null) {
            Comment parent = commentMapper.selectById(parentId);
            if (parent == null || parent.getStatus() != 1) {
                throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "父评论不存在或已删除");
            }
            if (!parent.getSongId().equals(dto.getSongId())) {
                throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "父评论不属于该歌曲");
            }
            // 两级结构：回复子评论时拍平挂到其父评论下
            if (parent.getParentId() != null) {
                parentId = parent.getParentId();
            }
        }

        Comment comment = new Comment();
        comment.setSongId(dto.getSongId());
        comment.setUserId(userId);
        comment.setParentId(parentId);
        comment.setContent(dto.getContent());
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);
        return comment.getId();
    }

    /** 删除（逻辑删）：本人或管理员，子评论级联逻辑删。 */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = AuthContext.requireLogin();
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "评论不存在");
        }
        if (AuthContext.getRole() != AuthContext.ROLE_ADMIN && !comment.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NO_PERMISSION);
        }
        comment.setStatus(0);
        commentMapper.updateById(comment);
        commentMapper.deleteChildren(id);
    }

    /** 点赞：Redis SADD 防重，重复点赞幂等；Redis 异常降级直加。 */
    public void like(Long id) {
        Long userId = AuthContext.requireLogin();
        mustGetComment(id);
        if (!markLike(id, String.valueOf(userId), true)) {
            return; // 已点赞，幂等返回
        }
        commentMapper.incrementLike(id);
    }

    /** 取消点赞：SREM 移除，计数-1 下限 0。 */
    public void unlike(Long id) {
        Long userId = AuthContext.requireLogin();
        mustGetComment(id);
        if (!markLike(id, String.valueOf(userId), false)) {
            return; // 未点赞过，幂等返回
        }
        commentMapper.decrementLike(id);
    }

    /** ---------- 私有 ---------- */

    private void mustGetComment(Long id) {
        Comment comment = commentMapper.selectById(id);
        if (comment == null) {
            throw new BizException(ErrorCode.PARAM_OUT_OF_RANGE, "评论不存在");
        }
    }

    /**
     * SADD/SREM 防重：返回 true 表示集合状态变化需调整计数；false 表示幂等命中无需调整。
     * Redis 异常时返回 true 放行计数（降级容忍少量重复）。
     */
    private boolean markLike(Long commentId, String member, boolean like) {
        String key = LIKE_KEY_PREFIX + commentId;
        try {
            Long changed = like
                    ? redis.opsForSet().add(key, member)
                    : redis.opsForSet().remove(key, member);
            return changed != null && changed > 0;
        } catch (Exception e) {
            log.warn("redis like-set degraded, key={}: {}", key, e.getMessage());
            return true;
        }
    }
}
