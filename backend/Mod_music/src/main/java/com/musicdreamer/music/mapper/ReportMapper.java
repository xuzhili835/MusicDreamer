package com.musicdreamer.music.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.musicdreamer.music.entity.Report;
import com.musicdreamer.music.vo.ReportItemVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** 举报 Mapper：管理列表 join user 拼举报人昵称。 */
public interface ReportMapper extends BaseMapper<Report> {

    String RPT_COLS = "r.id, r.reporter_id AS reporterId, COALESCE(u.nickname, u.username) AS reporterName, "
            + "r.target_type AS targetType, r.target_id AS targetId, r.reason, r.description, r.status, "
            + "r.handler_id AS handlerId, r.handle_result AS handleResult, "
            + "r.create_time AS createTime, r.handle_time AS handleTime";

    @Select("<script>SELECT " + RPT_COLS + " FROM report r LEFT JOIN user u ON u.id = r.reporter_id "
            + "WHERE 1 = 1 <if test='status != null'> AND r.status = #{status}</if> "
            + "ORDER BY r.create_time DESC LIMIT #{offset}, #{size}</script>")
    List<ReportItemVO> selectReportPage(@Param("status") Integer status,
                                        @Param("offset") long offset, @Param("size") int size);

    @Select("<script>SELECT COUNT(*) FROM report r WHERE 1 = 1 "
            + "<if test='status != null'> AND r.status = #{status}</if></script>")
    long countReports(@Param("status") Integer status);
}
