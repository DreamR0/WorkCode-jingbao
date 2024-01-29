package com.api.customization.qc2304359;

import com.alibaba.fastjson.JSON;
import com.engine.kq.biz.*;
import com.engine.kq.util.KQDurationCalculatorUtil;
import com.engine.kq.wfset.bean.SplitBean;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class KqReportUtil {

    /**
     * 计算出勤率
     *
     * @param workmins
     * @param attendanceHour
     * @return
     */
    public String attendanceRateCal(String workmins, String attendanceHour) {
        double workHourD = 0.00;
        double attendanceHourD = 0.00;
        String attendanceRate = "";
        NumberFormat nf = NumberFormat.getPercentInstance();
        try {
            if (workmins.isEmpty() || attendanceHour.isEmpty()) {
                //如果其中一个为空则返回值为空
                return "0%";
            } else {
                //如果都不为空则计算出勤率
                workHourD = Double.parseDouble(workmins)/60;
                attendanceHourD = Double.parseDouble(attendanceHour);

                nf.setMaximumFractionDigits(2);
                attendanceRate = nf.format((double) (Math.round((attendanceHourD / workHourD) * 100) / 100.0));
                new BaseBean().writeLog("==zj==(出勤率-计算公式-实际工作时长/应工作时长)" + attendanceHourD + " / " + workHourD + " = " + attendanceRate);
            }
        } catch (Exception e) {
            new BaseBean().writeLog("==zj==(出勤率计算报错)" + e);
        }
        return attendanceRate;
    }

    /**
     * 半小时长计算（最小单位0.5h）
     * @param Mins
     * @param calType 计算类型 0-向下取整 1-向上取整 2-四舍五入
     * @return
     */
    public String halfHourCal(String Mins,int calType) {
        double halfHour = 0.5;  //最小单位0.5h
        double absenceMinsD = 0.00;
        try {
            if (calType == 0){
                //向下取整
                if (Mins.isEmpty()) {
                    //如果为空
                    return "0.0";
                } else {
                    absenceMinsD = (Math.floor(Double.parseDouble(Mins) / (halfHour * 60.0)) * 30.0) / 60.0;
                }
            }

            if (calType == 1){
                //向上取整
                if (Mins.isEmpty()) {
                    //如果为空
                    return "0.0";
                } else {
                    absenceMinsD = (Math.ceil(Double.parseDouble(Mins) / (halfHour * 60.0)) * 30.0) / 60.0;
                }
            }

            if (calType == 2){
                //四舍五入
                if (Mins.isEmpty()) {
                    //如果为空
                    return "0.0";
                } else {
                    absenceMinsD = (Math.round(Double.parseDouble(Mins) / (halfHour * 60.0)) * 30.0) / 60.0;
                }
            }

        } catch (Exception e) {
            new BaseBean().writeLog("==zj==((最小单位0.5h)时长转换错误)" + e);
        }
        return absenceMinsD + "";
    }

    /**
     * 判断入离职缺勤
     * @param resourceId
     * @param fromDate
     * @param toDate
     * @return
     */
    public String resignationCal(String resourceId, String fromDate,String toDate) {
        RecordSet rs = new RecordSet();
        int days = 0;
        String resourceCreate = "";
        String resourceEnd = "";
        String sql = "";
        try {
            sql = "select createdate,endDate from hrmresource where id=" + resourceId;
            rs.executeQuery(sql);
            if (rs.next()) {
                //获取该人员账号的创建日期，以及离职日期
                resourceCreate = Util.null2String(rs.getString("createdate"));
                resourceEnd = Util.null2String(rs.getString("endDate"));
            }
            //获取入职之前的缺勤天数
            if (!"".equals(resourceCreate)){
                String createSql = "select kqdate  from (select * from kq_format_total where kqdate between '"+fromDate+"'"+
                                    " and '"+toDate+"' and workmins > 0)[table]"+
                                    "where kqdate < '"+resourceCreate+"'";
                new BaseBean().writeLog("createSQL:"+createSql);
                rs.executeQuery(createSql);
                days += rs.getCounts();
            }
            //获取离职之后的缺勤天数
            if (!"".equals(resourceEnd)){
                String endSql = "select kqdate  from (select * from kq_format_total where kqdate between '"+fromDate+"'"+
                        " and '"+toDate+"' and workmins > 0)[table]"+
                        "where kqdate < '"+resourceCreate+"'";
                new BaseBean().writeLog("endSql:"+endSql);
                rs.executeQuery(endSql);
                days += rs.getCounts();
            }
        } catch (Exception e) {
            new BaseBean().writeLog("==zj==(判断入离职缺勤报错)" + e);
        }
        return days*8+"";
    }

    /**
     * 计算所有调休类型时长
     * @param flowData
     * @return
     */
    public String leaveTotal(Map<String, Object> flowData, String id,String from) {
        double workminsLeave = 0.00;
        double leaveTotal = 0.00;
        KQLeaveRulesBiz kqLeaveRulesBiz = new KQLeaveRulesBiz();
        List<Map<String, Object>> allLeaveRules = kqLeaveRulesBiz.getAllLeaveRules();
        Map<String, Object> leaveRule = null;
        for (int i = 0; allLeaveRules != null && i < allLeaveRules.size(); i++) {
            leaveRule = (Map<String, Object>) allLeaveRules.get(i);
            new BaseBean().writeLog("==zj==(leaveRule)" + JSON.toJSONString(leaveRule));
            String flowType = Util.null2String("leaveType_" + leaveRule.get("id"));
            String leaveData = Util.null2String(flowData.get(id + "|" + flowType));
            String flowLeaveBackType = Util.null2String("leavebackType_" + leaveRule.get("id"));
            String leavebackData = Util.null2s(Util.null2String(flowData.get(id + "|" + flowLeaveBackType)), "0.0");
            String b_flowLeaveData = "";
            String flowLeaveData = "";
            try {
                //以防止出现精度问题
                if (leaveData.length() == 0) {
                    leaveData = "0.0";
                }
                if (leavebackData.length() == 0) {
                    leavebackData = "0.0";
                }
                BigDecimal b_leaveData = new BigDecimal(leaveData);
                BigDecimal b_leavebackData = new BigDecimal(leavebackData);
                b_flowLeaveData = b_leaveData.subtract(b_leavebackData).toString();
                if (Util.getDoubleValue(b_flowLeaveData, -1) < 0) {
                    b_flowLeaveData = "0.0";
                }
            } catch (Exception e) {
                new BaseBean().writeLog("==zj==(调休时长报错)" +e);
            }

            //考虑下冻结的数据
            if (b_flowLeaveData.length() > 0) {
                flowLeaveData = KQDurationCalculatorUtil.getDurationRound(b_flowLeaveData);
            } else {
                flowLeaveData = KQDurationCalculatorUtil.getDurationRound(Util.null2String(Util.getDoubleValue(leaveData, 0.0) - Util.getDoubleValue(leavebackData, 0.0)));
            }
            String leavaName = leaveRule.get("name").toString();    //获取请假名称

            //如果给假期属于统计范围，统计到事假总计
            try{
                if ("年假".equals(leavaName) || "事假".equals(leavaName) || "出差".equals(leavaName)){
                    if (flowLeaveData.isEmpty()){
                        leaveTotal += 0.00;
                    }else {
                        if ("年假".equals(leavaName)){
                            leaveTotal += Double.parseDouble(flowLeaveData)*8;
                        }else {
                            leaveTotal += Double.parseDouble(flowLeaveData);
                        }
                    }

                }

                //这里计算列“扣减否应出勤小时”所需的假期类型时长
                if ("workminsAfter".equals(from)){
                    if ("年假".equals(leavaName) || "出差".equals(leavaName)){
                        if (flowLeaveData.isEmpty()){
                            workminsLeave += 0.00;
                        }else {
                            if ("年假".equals(leavaName)){
                                workminsLeave += Double.parseDouble(flowLeaveData)*8;
                            }else {
                                workminsLeave += Double.parseDouble(flowLeaveData);
                            }
                        }
                        new BaseBean().writeLog("==zj==(扣减否应出勤小时--符合条件的调休时长)" + workminsLeave);
                        return workminsLeave+"";
                    }
                }
            }catch (Exception e){
                new BaseBean().writeLog("==zj==(事假总计累加报错)" + e);
            }
        }
        return leaveTotal+"";
    }

    /**
     * 这里获取当天类型
     * @param resourceId
     * @param date
     * @return
     */
    public  int getChangeType(String resourceId, String date) {
        int changeType = -1;
        //1-节假日、2-工作日、3-休息日
        /*获取考勤组的ID，因为考勤组有有效期，所以需要传入日期*/
        KQGroupMemberComInfo kqGroupMemberComInfo = new KQGroupMemberComInfo();
        String groupId = kqGroupMemberComInfo.getKQGroupId(resourceId, date);

        /*该人员不存在于任意一个考勤组中，请为其设置考勤组*/
        if(groupId.equals("")){
            new BaseBean().writeLog("该人员不存在于任意一个考勤组中，请为其设置考勤组。resourceId=" + resourceId + ",date=" + date);
        }

        changeType = KQHolidaySetBiz.getChangeType(groupId, date);
        if (changeType != 1 && changeType != 2 && changeType != 3) {
            KQWorkTime kqWorkTime = new KQWorkTime();
            changeType = kqWorkTime.isWorkDay(resourceId, date) ? 2 : 3;
        }
        return changeType;
    }

    /**
     * 计算休息日或节假日的出差时长（08:15:00 - 17:15:00 有效时间）
     * 这里把日期类型也设置一下
     * @param flowfromtimeIndex
     * @param flowendtimeIndex
     * @return
     */
    public int evectionFlowCal(int flowfromtimeIndex, int flowendtimeIndex, SplitBean splitBean){
        int flowMins = 0;
        try{
            KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();
            int[] initArrays = kqTimesArrayComInfo.getInitArr();
            //休息时间段
            int restFromTime = kqTimesArrayComInfo.getArrayindexByTimes(Util.null2String(new BaseBean().getPropValue("qc2304359","restFromTime")));
            int restToTime = kqTimesArrayComInfo.getArrayindexByTimes(Util.null2String(new BaseBean().getPropValue("qc2304359","restToTime")));
            //休息日或节假日 出差流程有效时间段
            int NonWorkFromIndex = kqTimesArrayComInfo.getArrayindexByTimes(Util.null2String(new BaseBean().getPropValue("qc2304359","nonWorkFromTime")));
            int nonWorkToTimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(Util.null2String(new BaseBean().getPropValue("qc2304359","nonWorkToTime")));
            new BaseBean().writeLog("==zj==(出差流程有效时间段)" + NonWorkFromIndex +" | " + nonWorkToTimeIndex);
            //先填充流程时间段
            Arrays.fill(initArrays, flowfromtimeIndex,flowendtimeIndex,1);
            //再填充休息时间段
            Arrays.fill(initArrays, restFromTime,restToTime,-2);
            //在有效时间段内获取分钟数
             flowMins = kqTimesArrayComInfo.getCnt(initArrays, NonWorkFromIndex, nonWorkToTimeIndex, 1);

             //这里把日期类型也设置一下，方便归类
            int changeType = getChangeType(splitBean.getResourceId(), splitBean.getFromDate());
            splitBean.setChangeType(changeType);
        }catch (Exception e){
            new BaseBean().writeLog("==zj==(出差流程非工作日时长计算报错)" + e);
        }
        return flowMins;
    }

    /**
     * 这里给三个日期类型的出差列赋值
     * @param changeType
     * @param resourceId
     * @return
     */
    public String evectionCol(String resourceId,int changeType,String fromDate,String toDate){
        Double evectionValue = 0.00;
        RecordSet rs = new RecordSet();
        String sql = "";
        try{
            sql = "select * from kq_flow_split_evection where changeType = " + changeType + " and resourceid = "+resourceId + " and fromdate between '"+fromDate+"' and '"+toDate+"'";
            new BaseBean().writeLog("==zj==(三个出差列的计算sql)" + sql);
            rs.executeQuery(sql);
            while (rs.next()){
                evectionValue += rs.getDouble("duration");
            }
        }catch (Exception e){
            new BaseBean().writeLog("==zj==(出差列赋值报错)" + e);
        }
        return evectionValue+"";
    }

    /**
     *计算交补天数
     * @param resoureId
     * @return
     */
    public String workDaysRealCal(String resoureId,String fromDate,String toDate){
        RecordSet rs = new RecordSet();
        String sql = "";
        int days = 0;
        try{
            sql = "select count (DISTINCT(signDate)) as days from HrmScheduleSign where userId = " + resoureId + " and signDate between '"+fromDate+"' and '"+toDate+"'";
            new BaseBean().writeLog("==zj==(交补天数计算)" + sql);
            rs.executeQuery(sql);
            if (rs.next()){
                days = rs.getInt("days");
            }

        }catch (Exception e){
            new BaseBean().writeLog("==zj==(计算交补天数错误)" + e);
        }

        return days+"";
    }

    /**
     * 这里计算实际工作时长，系统标准上+加班时长+周末出差
     * @param attendanceMins
     * @param resourceId
     * @return
     */
    public String attendanceMinsCal(int attendanceMins,String resourceId,String fromDate,String toDate){
        RecordSet rs = new RecordSet();
        String sql = "";
        int overTimeMins = 0;
        int evectionMins = 0;
        Double attendanceHour = 0.00;
        Double overTimeHour = 0.00;
        Double evectionHour = 0.00;
        try{
            //查询当前人员范围内的所有加班时长
            sql = "select * from kq_flow_overtime where resourceid = '"+resourceId+"' and fromdate between '"+fromDate+"' and '"+toDate+"'";
            new BaseBean().writeLog("==zj==(该人员所有加班时长sql)" + sql);
            rs.executeQuery(sql);
            while (rs.next()){
                overTimeMins += rs.getInt("duration_min");
            }
            //这里对加班时长进行换算（0.5h,向下取整）
            if (overTimeMins != 0){
                String overTimeHalfHour = halfHourCal(overTimeMins+"",0);
                overTimeHour = Double.parseDouble(overTimeHalfHour);
            }

            //查询当前人员范围内的所有周末出差时长
            sql = "select * from kq_flow_split_evection where resourceid = '"+resourceId+"' and fromdate between '"+fromDate+"' and '"+toDate+"' and changetype = 3";
            new BaseBean().writeLog("==zj==(所有周末出差时长)" + sql);
            rs.executeQuery(sql);
            while (rs.next()){
                evectionMins += rs.getInt("D_Mins");
            }
           //这里对周末出差时长进行换算(0.5h,向下换算)
            if (evectionMins != 0){
                String evectionHalfHour = halfHourCal(evectionMins+"",0);
                evectionHour = Double.parseDouble(evectionHalfHour);
            }


            //这里对实际出勤时长进行换算
            if (attendanceMins > 0){
                attendanceHour = attendanceMins / 30.0;
                attendanceHour = Double.parseDouble(String.format("%.2f",attendanceHour));
                attendanceHour = Double.parseDouble(String.format("%.2f",attendanceHour * 30 /60));
            }


            //实际出勤时长汇总计算
            attendanceHour += overTimeHour + evectionHour;

            //这里对实际出勤时长进行四舍五入，保留两位小数
            if (attendanceHour > 0){
                attendanceHour = Double.parseDouble(String.format("%.2f",attendanceHour));
            }else {
                attendanceHour = 0.00;
            }
            new BaseBean().writeLog("==zj==(格式化实际出勤时长)" + attendanceHour);
             new BaseBean().writeLog("==zj==(实际出勤时长计算公式：系统标准+所有加班时长+周末出差)" + attendanceHour + " | " + overTimeHour + " | " + evectionHour);


        }catch (Exception e){
            new BaseBean().writeLog("==zj==(实际工作时长计算错误)" + e);
        }
        return  attendanceHour+"";
    }

    /**
     * 对考勤日期时长进行加减
     * @param KqDate
     * @return
     */
    public String DateCal(String KqDate){
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        Date kqDate = null;
        try{
             kqDate = sdf.parse(KqDate);
            Calendar calendar = Calendar.getInstance(); //使用Calendar日历类对日期进行加减
            calendar.setTime(kqDate);
            calendar.add(Calendar.DAY_OF_MONTH, +1);
            kqDate = calendar.getTime();

        }catch (Exception e){
            new BaseBean().writeLog("==zj==(考勤日期加减错误)" + e);
        }

        return sdf.format(kqDate);
    }

    /**
     * 计算非工作日的工作时长
     * @param signInDate
     * @param signInTime
     * @param signOutDate
     * @param signOutTime
     * @return
     */
    public Double kqMins(String signInDate,String signInTime,String signOutDate,String signOutTime){
        int signinTimeIndex = 0;
        int signoutTimeIndex = 0;
        Double kqHour = 0.00;
        try{

            KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();

            signinTimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(signInTime);
            if (signOutDate.compareTo(signInDate) >= 1){
                signoutTimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(signOutTime) + 1440;
            }else {
                signoutTimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(signOutTime);
            }
            kqHour = (signoutTimeIndex - signinTimeIndex)/60.0;
        }catch (Exception e){
            new BaseBean().writeLog("==zj==(非工作日时长计算报错)" +e);
        }

        return kqHour;
    }

    /**
     * 计算扣减后因工作时长的出差和年假时长
     * @param flowData
     * @return
     */
    public String leaveCount(Map<String, Object> flowData, String id) {
        double leaveTotal = 0.00;
        KQLeaveRulesBiz kqLeaveRulesBiz = new KQLeaveRulesBiz();
        List<Map<String, Object>> allLeaveRules = kqLeaveRulesBiz.getAllLeaveRules();
        Map<String, Object> leaveRule = null;
        for (int i = 0; allLeaveRules != null && i < allLeaveRules.size(); i++) {
            leaveRule = (Map<String, Object>) allLeaveRules.get(i);
            new BaseBean().writeLog("==zj==(计算扣减--leaveRule)" + JSON.toJSONString(leaveRule));
            String flowType = Util.null2String("leaveType_" + leaveRule.get("id"));
            String leaveData = Util.null2String(flowData.get(id + "|" + flowType));
            String flowLeaveBackType = Util.null2String("leavebackType_" + leaveRule.get("id"));
            String leavebackData = Util.null2s(Util.null2String(flowData.get(id + "|" + flowLeaveBackType)), "0.0");
            String b_flowLeaveData = "";
            String flowLeaveData = "";
            try {
                //以防止出现精度问题
                if (leaveData.length() == 0) {
                    leaveData = "0.0";
                }
                if (leavebackData.length() == 0) {
                    leavebackData = "0.0";
                }
                BigDecimal b_leaveData = new BigDecimal(leaveData);
                BigDecimal b_leavebackData = new BigDecimal(leavebackData);
                b_flowLeaveData = b_leaveData.subtract(b_leavebackData).toString();
                if (Util.getDoubleValue(b_flowLeaveData, -1) < 0) {
                    b_flowLeaveData = "0.0";
                }
            } catch (Exception e) {
                new BaseBean().writeLog("==zj==(调休时长报错)" +e);
            }

            //考虑下冻结的数据
            if (b_flowLeaveData.length() > 0) {
                flowLeaveData = KQDurationCalculatorUtil.getDurationRound(b_flowLeaveData);
            } else {
                flowLeaveData = KQDurationCalculatorUtil.getDurationRound(Util.null2String(Util.getDoubleValue(leaveData, 0.0) - Util.getDoubleValue(leavebackData, 0.0)));
            }
            String leavaName = leaveRule.get("name").toString();    //获取请假名称

            //如果给假期属于统计范围，统计到事假总计
            try{
                if ("年假".equals(leavaName) || "出差".equals(leavaName)){
                    if (flowLeaveData.isEmpty()){
                        leaveTotal += 0.00;
                    }else {
                        leaveTotal += Double.parseDouble(flowLeaveData);
                    }

                }
            }catch (Exception e){
                new BaseBean().writeLog("==zj==(计算扣减累加报错)" + e);
            }
        }
        return leaveTotal+"";
    }

    /**
     * 计算早退迟到时长，每条记录取整完再进行累加
     * @return
     */
    public String getAbsence(String id, String fromDate,String toDate,String fieldName){
        Double sum = 0.00;
        RecordSet rs = new RecordSet();
        String sql = "";

        //这里再判断下是否来自导出
        if ("32month".equals(fieldName)){
            fieldName = "beLateMins";
        }
        if ("36month".equals(fieldName)){
            fieldName = "leaveEarlyMins";
        }

        try {
            sql = "select "+fieldName+" from kq_format_total where resourceid = '"+id+"' and kqdate between '"+fromDate+"' and '"+toDate+"'";
            new BaseBean().writeLog("==zj==(早退迟到时长sql)" + JSON.toJSONString(sql));
            rs.executeQuery(sql);
            while (rs.next()){
                String Mins = Util.null2String(rs.getString(fieldName));
                Double MinsD = Double.parseDouble(halfHourCal(Mins, 1));
                sum+=MinsD;
            }
        } catch (Exception e) {
            new BaseBean().writeLog("==zj==(早退迟到时长计算错误)" + JSON.toJSONString(e));
        }
        return sum+"";
    }
}
