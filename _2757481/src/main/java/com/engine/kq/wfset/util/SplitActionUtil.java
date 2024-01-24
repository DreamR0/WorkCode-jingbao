package com.engine.kq.wfset.util;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.fastjson.JSON;
import com.engine.kq.bean.KQHrmScheduleSign;
import com.engine.kq.biz.KQBalanceOfLeaveBiz;
import com.engine.kq.biz.KQFlowDataBiz;
import com.engine.kq.biz.KQOverTimeRuleCalBiz;
import com.engine.kq.biz.KQOvertimeRulesBiz;
import com.engine.kq.biz.KQScheduleSignBiz;
import com.engine.kq.biz.KQSettingsBiz;
import com.engine.kq.biz.KQShiftPersonalizedRuleCominfo;
import com.engine.kq.biz.KQShiftPersonalizedRuleDetailComInfo;
import com.engine.kq.biz.KQShiftRuleInfoBiz;
import com.engine.kq.biz.KQTimesArrayComInfo;
import com.engine.kq.biz.KQWorkTime;
import com.engine.kq.biz.chain.duration.WorkHalfUnitSplitChain;
import com.engine.kq.biz.chain.shiftinfo.ShiftInfoBean;
import com.engine.kq.entity.KQOvertimeRulesDetailEntity;
import com.engine.kq.enums.DurationTypeEnum;
import com.engine.kq.log.KQLog;
import com.engine.kq.timer.KQQueue;
import com.engine.kq.timer.KQTaskBean;
import com.engine.kq.util.KQDurationCalculatorUtil;
import com.engine.kq.wfset.bean.OvertimeBalanceTimeBean;
import com.engine.kq.wfset.bean.SplitBean;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.BeanUtils;
import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;

public class SplitActionUtil extends BaseBean {
  public static KQLog kqLog = new KQLog();

  private static BaseBean baseBean = new BaseBean();
  /**
   * 现在先用正常的去执行吧
   * @param splitBeans
   * @param requestid
   */
  public static void handleLeaveAction(List<SplitBean> splitBeans, String requestid) {
    for(int i = 0 ; i < splitBeans.size() ; i++){
      SplitBean splitBean = splitBeans.get(i);
      String resourceId = splitBean.getResourceId();
      String duration = splitBean.getDuration();
      String newLeaveType = splitBean.getNewLeaveType();
      String durationrule = splitBean.getDurationrule();
      String fromdate = splitBean.getFromDate();
      String fromdatedb = splitBean.getFromdatedb();
      String reduceMonth = splitBean.getReduceMonth();
      kqLog.info("请假扣减:resourceId:"+resourceId+":fromdate:"+fromdate+":duration:"+duration+":newLeaveType:"+newLeaveType+":durationrule:"+durationrule+":requestid:"+requestid+":fromdatedb:"+fromdatedb+":reduceMonth:"+reduceMonth);
      //==z
      if (reduceMonth.length() > 0){
        KQBalanceOfLeaveBiz.addUsedAmount(resourceId, fromdate, newLeaveType, duration, "",requestid,fromdatedb,reduceMonth);
      }else {
        KQBalanceOfLeaveBiz.addUsedAmount(resourceId, fromdate, newLeaveType, duration, "",requestid,fromdatedb);
      }
    }
  }

  /**
   * 单独针对加班规则的第二种模式 生成加班数据
   * @param splitBeans
   * @param requestid
   */
  public static void handleOverTimeActionMode2(List<SplitBean> splitBeans, String requestid) {
    clearSameRequestTX(requestid);
    KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();
    List<String> overKeys = new ArrayList<>();
    for(int i = 0 ; i < splitBeans.size() ; i++){
      SplitBean splitBean = splitBeans.get(i);
      int computingMode = Util.getIntValue(splitBean.getComputingMode());
      if(computingMode == 2 || computingMode == 4){
        //判断流程对应的日期下有没有打卡数据，如果有的话，需要生成相应的调休数据
        String belongDate = splitBean.getBelongDate();
        String resourceId = splitBean.getResourceId();
        String fromtime = splitBean.getFromTime();
        int fromtimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(fromtime);
        String key = resourceId+"_"+belongDate+"_"+ belongDate;
        if(fromtimeIndex > 1439){
          //跨天了
          key = resourceId+"_"+belongDate+"_"+ DateUtil.addDate(belongDate, 1);
        }
        if(!overKeys.contains(key)){
          overKeys.add(key);
        }
      }
      if(computingMode == 1){
        doComputingMode1_splitBean(splitBean);
      }
    }
    KQOverTimeRuleCalBiz kqOvertimeCalBiz = new KQOverTimeRuleCalBiz();
    kqLog.info("加班生成调休:handleOverTimeActionMode2 overKeys:"+JSON.toJSONString(overKeys));
    if(overKeys != null && !overKeys.isEmpty()){
      for(String key : overKeys){
        String[] keys = key.split("_",-1);
        if(keys.length == 3){
          String resourceId = keys[0];
          String belongDate = keys[1];
          String belongToDate = keys[2];
          kqOvertimeCalBiz.buildOvertime(resourceId, belongDate, belongToDate,"加班流程生成加班#flow,requestId:"+requestid);
        }
      }
    }
  }

  public static void turnOvertimeBeansWithRule(List<SplitBean> splitBeans){

    Map<String,List<SplitBean>> resourceMap = new HashMap<>();
    //公众假日加班时长
    double D_Pub_Duration = 0.0;
    double D_Pub_Mins = 0.0;
    //工作日加班时长
    double D_Work_Duration = 0.0;
    double D_Work_Mins = 0.0;
    //休息日加班时长
    double D_Rest_Duration = 0.0;
    double D_Rest_Mins = 0.0;

    //公众假日加班
    List<SplitBean> holiday = new ArrayList<>();
    //工作日加班
    List<SplitBean> work = new ArrayList<>();
    //休息日加班
    List<SplitBean> rest = new ArrayList<>();

    String pub_date = "";
    String work_date = "";
    String rest_date = "";

    //每一天的流程时长都在这里了，搞吧
    for(SplitBean sb : splitBeans){
//   * 1-公众假日、2-工作日、3-休息日
      String tmpResid = sb.getResourceId();
      if(resourceMap.containsKey(tmpResid)){
        resourceMap.get(tmpResid).add(sb);
      }else{
        List<SplitBean> splitBeans_tmp = new ArrayList<>();
        splitBeans_tmp.add(sb);
        resourceMap.put(tmpResid, splitBeans_tmp);
      }
    }
    if(!resourceMap.isEmpty()){
      for(Map.Entry<String,List<SplitBean>> me : resourceMap.entrySet()){
        String resourceid = me.getKey();
        List<SplitBean> tmp_splitbeans = me.getValue();
        for(SplitBean sb : tmp_splitbeans){
//   * 1-公众假日、2-工作日、3-休息日
          int changeType = sb.getChangeType();
          double durations = Util.getDoubleValue(sb.getDuration(), 0.0);
          double durationMins = sb.getD_Mins();
          if(1 == changeType){
            D_Pub_Duration += durations;
            D_Pub_Mins += durationMins;
            pub_date = sb.getBelongDate();
            holiday.add(sb);
          }
          if(2 == changeType){
            D_Work_Duration += durations;
            D_Work_Mins += durationMins;
            work_date = sb.getBelongDate();
            work.add(sb);
          }
          if(3 == changeType){
            D_Rest_Duration += durations;
            D_Rest_Mins += durationMins;
            rest_date = sb.getBelongDate();
            rest.add(sb);
          }
        }

        if(D_Pub_Duration > 0){
          int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceid, pub_date);
          if(D_Pub_Mins < minimumUnit){
            for(SplitBean tmp : holiday){
              tmp.setDuration("0.0");
              tmp.setD_Mins(0.0);
            }
          }
        }
        if(D_Work_Duration > 0){
          int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceid, work_date);
          if(D_Work_Mins < minimumUnit){
            for(SplitBean tmp : work){
              tmp.setDuration("0.0");
              tmp.setD_Mins(0.0);
            }
          }
        }
        if(D_Rest_Duration > 0){
          int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceid, rest_date);
          if(D_Rest_Mins < minimumUnit){
            for(SplitBean tmp : rest){
              tmp.setDuration("0.0");
              tmp.setD_Mins(0.0);
            }
          }
        }
      }
    }

  }

  /**
   * 生成加班数据
   * @param splitBeans
   * @param requestid
   * @param canMode2 是否是归档节点 归档节点才可以把加班数据写到加班中间表,不是归档节点的话意味着只生成调休不生成加班,只有强制归档canMode2才是true
   * @param from_uuid 来自action的uuid
   */
  public static void handleOverTimeAction(List<SplitBean> splitBeans, String requestid,
      boolean canMode2, String from_uuid) throws Exception{
//    保证同一个requestid能重复生成调休和加班
    if(canMode2){
      clearSameRequestTX(requestid);
    }
    KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();
    RecordSet rs = new RecordSet();
    List<String> overKeys = new ArrayList<>();
    for(int i = 0 ; i < splitBeans.size() ; i++){
      SplitBean splitBean = splitBeans.get(i);
      String resourceId = splitBean.getResourceId();
      String belongDate = splitBean.getBelongDate();
      String toDate = splitBean.getToDate();
      String duration = splitBean.getDuration();
      String durationrule = splitBean.getDurationrule();

      int computingMode = Util.getIntValue(splitBean.getComputingMode());
      int changeType = splitBean.getChangeType();
      String paidSql = "insert into KQ_PAID_VACATION (requestId,workflowId,dataid,detailid,resourceId,fromDate,fromTime,toDate,toTime,duration,durationrule,computingMode,changeType)"+
          " values(?,?,?,?,?,?,?,?,?,?,?,?,?) ";
      boolean isLog = rs.executeUpdate(paidSql, splitBean.getRequestId(), splitBean.getWorkflowId(),
          splitBean.getDataId(), splitBean.getDetailId(), splitBean.getResourceId(),
          splitBean.getFromDate(), splitBean.getFromTime(), splitBean.getToDate(),
          splitBean.getToTime(), splitBean.getDuration(),splitBean.getDurationrule(), computingMode,changeType);
      if(isLog){
        kqLog.info("加班生成调休:resourceId:"+resourceId+":computingMode:"+computingMode+":belongDate:"+belongDate+":duration:"+duration+":durationrule:"+durationrule+":requestid:"+requestid);

        if(computingMode == 1){
          doComputingMode1_4TX(splitBean);
          if(canMode2){
            doComputingMode1_splitBean(splitBean);
          }
        }
        if(canMode2 && (computingMode == 2 || computingMode == 4)){
          //判断流程对应的日期下有没有打卡数据，如果有的话，需要生成相应的调休数据
          String fromtime = splitBean.getFromTime();
          int fromtimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(fromtime);
          String key = resourceId+"_"+belongDate+"_"+ belongDate;
          if(fromtimeIndex > 1439){
            //跨天了
            key = resourceId+"_"+belongDate+"_"+ DateUtil.addDate(belongDate, 1);
          }
          if(!overKeys.contains(key)){
            overKeys.add(key);
          }
        }
      }
    }
    KQOverTimeRuleCalBiz kqOvertimeCalBiz = new KQOverTimeRuleCalBiz();
    kqLog.info("加班生成调休:handleOverTimeAction overKeys:"+JSON.toJSONString(overKeys));
    if(overKeys != null && !overKeys.isEmpty()){
      for(String key : overKeys){
        String[] keys = key.split("_",-1);
        if(keys.length == 3){
          String resourceId = keys[0];
          String belongDate = keys[1];
          String belongToDate = keys[2];
          kqOvertimeCalBiz.buildOvertime(resourceId, belongDate, belongToDate, "加班流程生成加班#flow,requestId:"+requestid+"#from_uuid|"+from_uuid);
        }
      }
    }
  }

  /**
   * 保证同一个requestid能重复生成调休和加班
   * @param requestid
   */
  public static void clearSameRequestTX(String requestid) {
    String all_tiaoxiuids = "";
    List<String>  all_tiaoxiuidList = Lists.newArrayList();
    RecordSet rs = new RecordSet();
    String sql = "select * from kq_flow_overtime where requestid = ? ";
    rs.executeQuery(sql, requestid);
    while (rs.next()){
      String tiaoxiuid = Util.null2String(rs.getString("tiaoxiuid"),"");
      if(tiaoxiuid.length() > 0 && Util.getIntValue(tiaoxiuid) > 0){
        all_tiaoxiuids += ","+tiaoxiuid;
        all_tiaoxiuidList.add(tiaoxiuid);
      }
    }
    if(all_tiaoxiuids.length() > 0){
      all_tiaoxiuids = all_tiaoxiuids.substring(1);
      String tiaoxiuidis0 = "";
      String delSql0 = "select * from kq_balanceofleave where "+Util.getSubINClause(all_tiaoxiuids, "id", "in")+" and "
          + " baseamount =0 and extraamount=0 and usedamount=0 and baseamount2=0 and extraamount2=0 and usedamount2=0 ";

      if(rs.getDBType().equalsIgnoreCase("oracle")) {
        delSql0 = "select * from kq_balanceofleave where "+Util.getSubINClause(all_tiaoxiuids, "id", "in")+" and "
            + " nvl(baseamount,0) =0 and nvl(extraamount,0)=0 and nvl(usedamount,0)=0 and nvl(baseamount2,0)=0 "
            + " and nvl(extraamount2,0)=0 and nvl(usedamount2,0)=0 ";
      }else if((rs.getDBType()).equalsIgnoreCase("mysql")){
        delSql0 = "select * from kq_balanceofleave where "+Util.getSubINClause(all_tiaoxiuids, "id", "in")+" and "
            + " ifnull(baseamount,0) =0 and ifnull(extraamount,0)=0 and ifnull(usedamount,0)=0 and ifnull(baseamount2,0)=0 "
            + " and ifnull(extraamount2,0)=0 and ifnull(usedamount2,0)=0 ";
      }else {
        delSql0 = "select * from kq_balanceofleave where "+Util.getSubINClause(all_tiaoxiuids, "id", "in")+" and "
            + " isnull(baseamount,0) =0 and isnull(extraamount,0)=0 and isnull(usedamount,0)=0 and isnull(baseamount2,0)=0 "
            + " and isnull(extraamount2,0)=0 and isnull(usedamount2,0)=0 ";
      }
      rs.executeQuery(delSql0);
      kqLog.info("all_tiaoxiuidList:"+all_tiaoxiuidList);
      kqLog.info("clearSameRequestTX:"+delSql0);
      while (rs.next()){
        String tiaoxiuid = Util.null2String(rs.getString("id"),"");
        if(tiaoxiuid.length() > 0 && Util.getIntValue(tiaoxiuid) > 0){
          tiaoxiuidis0 += ","+tiaoxiuid;
          all_tiaoxiuidList.remove(tiaoxiuid);
        }
      }
      kqLog.info("all_tiaoxiuidList:"+all_tiaoxiuidList);
      kqLog.info("tiaoxiuidis0:"+tiaoxiuidis0);
      if(tiaoxiuidis0.length() > 0){
        tiaoxiuidis0 = tiaoxiuidis0.substring( 1);
        String delSql = "delete from kq_balanceofleave where "+Util.getSubINClause(tiaoxiuidis0, "id", "in");
        boolean isok = rs.executeUpdate(delSql);
        kqLog.info("delSql:"+delSql+":isok:"+isok);
      }
      if(!all_tiaoxiuidList.isEmpty()){
        String clear_tiaoxiuids = all_tiaoxiuidList.stream().collect(Collectors.joining(","));
        String clearSql = "update kq_balanceofleave set tiaoxiuamount=0.0 where "+Util.getSubINClause(clear_tiaoxiuids, "id", "in");
        boolean isclearOk = rs.executeUpdate(clearSql);
        kqLog.info("clearSql:"+clearSql+":isclearOk:"+isclearOk);
      }

      String delUsageSql = "delete from kq_usagehistory where "+Util.getSubINClause(all_tiaoxiuids, "balanceofleaveid", "in");
      boolean isdelUsageOk = rs.executeUpdate(delUsageSql);
      kqLog.info("delUsageSql:"+delUsageSql+":isdelUsageOk:"+isdelUsageOk);
    }
    String delSql = "delete from kq_flow_overtime where requestid = ? ";
    boolean isDelOk = rs.executeUpdate(delSql,requestid);
    kqLog.info("delSql:"+delSql+":requestid:"+requestid+":isDelOk:"+isDelOk);
  }

  /**
   * 加班方式是第一种，如果是先生成调休，后面才生成加班，需要先把调休给按照加班规则给生成出来
   * @param splitBean
   */
  public static void doComputingMode1_4TX(SplitBean splitBean) {
    RecordSet rs = new RecordSet();
    int changeType = splitBean.getChangeType();
    String resourceId = splitBean.getResourceId();
    String belongDate = splitBean.getBelongDate();
    String duration = splitBean.getDuration();
    String fromdateDB = splitBean.getFromdatedb();
    String requestId = splitBean.getRequestId();
    String fromdate = splitBean.getFromDate();
    String fromtime = splitBean.getFromTime();
    String todate = splitBean.getToDate();
    String totime = splitBean.getToTime();
    String overtime_type = splitBean.getOvertime_type();
    double D_Mins = splitBean.getD_Mins();
    int workMins = splitBean.getWorkmins();
    String workingHours = Util.null2String(workMins/60.0);
    Map<String,Integer> changeTypeMap = Maps.newHashMap();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    KQOverTimeRuleCalBiz kqOverTimeRuleCalBiz = new KQOverTimeRuleCalBiz();
    Map<String, KQOvertimeRulesDetailEntity> overRulesDetailMap = Maps.newHashMap();
    Map<String, List<String[]>> restTimeMap = Maps.newHashMap();
    Map<String,Integer> computingModeMap = Maps.newHashMap();
    //先获取一些前提数据，加班規則和假期規則
    kqOverTimeRuleCalBiz.getOverTimeDataMap(resourceId, belongDate, belongDate, dateFormatter,changeTypeMap,overRulesDetailMap,restTimeMap,computingModeMap);

    String changeType_key = belongDate + "_" + changeType;
    if(!overRulesDetailMap.containsKey(changeType_key)){
      return;
    }
//   根据加班单位重新生成下加班分钟数
    D_Mins = kqOverTimeRuleCalBiz.getD_MinsByUnit(D_Mins);

    KQOvertimeRulesDetailEntity kqOvertimeRulesDetailEntity = overRulesDetailMap.get(changeType_key);
    int paidLeaveEnable = kqOverTimeRuleCalBiz.getPaidLeaveEnable(kqOvertimeRulesDetailEntity, overtime_type);
    boolean needSplitByTime = kqOverTimeRuleCalBiz.getNeedSplitByTime(kqOvertimeRulesDetailEntity, paidLeaveEnable);
    if(needSplitByTime) {
      List<OvertimeBalanceTimeBean> overtimeBalanceTimeBeans = kqOverTimeRuleCalBiz
          .getOvertimeBalanceTimeBean(resourceId, fromdate, fromtime, todate, totime, changeTypeMap,
              kqOvertimeRulesDetailEntity, splitBean, restTimeMap,overRulesDetailMap);
      if (overtimeBalanceTimeBeans != null && !overtimeBalanceTimeBeans.isEmpty()) {
        for(int i = 0 ; i < overtimeBalanceTimeBeans.size() ;i++){
          OvertimeBalanceTimeBean overtimeBalanceTimeBean = overtimeBalanceTimeBeans.get(i);
          String timepoint = overtimeBalanceTimeBean.getTimepoint();
          boolean needTX = overtimeBalanceTimeBean.isNeedTX();
          if(needTX){
            Map<String,Object> otherParam = Maps.newHashMap();
            otherParam.put("overtime_type", overtime_type);
            otherParam.put("OvertimeBalanceTimeBean", overtimeBalanceTimeBean);
            int timepoint_mins = overtimeBalanceTimeBean.getTimepoint_mins();
            if(timepoint_mins == 0){
              continue;
            }
            String tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,belongDate,timepoint_mins+"","0",workingHours,requestId,"1",fromdateDB,otherParam);
            if(Util.getIntValue(tiaoxiuId) > 0){
              //为啥要用kq_overtime_tiaoxiu这个表呢，因为kqpaidleaveaction可能和splitaction不是在同一个归档前节点
              String split_key = splitBean.getRequestId()+"_"+splitBean.getDataId()+"_"+splitBean.getDetailId()+"_"
                  +splitBean.getFromDate()+"_"+splitBean.getFromTime()+"_"+splitBean.getToDate()+"_"+
                  splitBean.getToTime()+"_"+splitBean.getD_Mins()+"_"+timepoint;
              String tiaoxiuId_sql = "insert into kq_overtime_tiaoxiu(split_key,tiaoxiu_id) values(?,?) ";
              rs.executeUpdate(tiaoxiuId_sql, split_key,tiaoxiuId);
              kqLog.info("doComputingMode1 加班生成调休成功！！！");
            }else{
              kqLog.info("doComputingMode1 加班生成调休失败！！！");
            }
          }
        }
      }else{
        //设置了按照时间区间生成调休，但是没有时间区间
      }
    }else{
      Map<String,Object> otherParam = Maps.newHashMap();
      otherParam.put("overtime_type", overtime_type);

      String tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,belongDate,D_Mins+"","0",workingHours,requestId,"1",fromdateDB,otherParam);
      if(Util.getIntValue(tiaoxiuId) > 0){
        //为啥要用kq_overtime_tiaoxiu这个表呢，因为kqpaidleaveaction可能和splitaction不是在同一个归档前节点
        String split_key = splitBean.getRequestId()+"_"+splitBean.getDataId()+"_"+splitBean.getDetailId()+"_"
            +splitBean.getFromDate()+"_"+splitBean.getFromTime()+"_"+splitBean.getToDate()+"_"+
            splitBean.getToTime()+"_"+splitBean.getD_Mins();
        String tiaoxiuId_sql = "insert into kq_overtime_tiaoxiu(split_key,tiaoxiu_id) values(?,?) ";
        rs.executeUpdate(tiaoxiuId_sql, split_key,tiaoxiuId);
        kqLog.info("doComputingMode1 加班生成调休成功！！！");
      }else{
        kqLog.info("doComputingMode1 加班生成调休失败！！！");
      }
    }

  }

  /**
   * 加班方式是第一种，然后再流程归档的时候把加班数据写到加班中间表里
   * @param splitBean
   */
  public static void doComputingMode1_splitBean(SplitBean splitBean) {
    try{
      kqLog.info("doComputingMode1_splitBean:splitBean: "+ (splitBean != null ? JSON.toJSONString(splitBean) : "null"));
      RecordSet rs = new RecordSet();
      RecordSet rs1 = new RecordSet();
      int changeType = splitBean.getChangeType();
      String requestId = splitBean.getRequestId();
      String resourceId = splitBean.getResourceId();
      String belongDate = splitBean.getBelongDate();
      String duration = splitBean.getDuration();
      String durationrule = splitBean.getDurationrule();
      String fromdateDB = splitBean.getFromdatedb();
      String fromtimedb = splitBean.getFromtimedb();
      String todatedb = splitBean.getTodatedb();
      String totimedb = splitBean.getTotimedb();
      String fromdate = splitBean.getFromDate();
      String fromtime = splitBean.getFromTime();
      String todate = splitBean.getToDate();
      String totime = splitBean.getToTime();
      double D_Mins = splitBean.getD_Mins();
      Map<String,Integer> changeTypeMap = Maps.newHashMap();
      DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
      KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();
      KQOverTimeRuleCalBiz kqOverTimeRuleCalBiz = new KQOverTimeRuleCalBiz();
      Map<String, KQOvertimeRulesDetailEntity> overRulesDetailMap = Maps.newHashMap();
      Map<String, List<String[]>> restTimeMap = Maps.newHashMap();
      Map<String,Integer> computingModeMap = Maps.newHashMap();
      //先获取一些前提数据，加班規則和假期規則
      kqOverTimeRuleCalBiz.getOverTimeDataMap(resourceId, belongDate, belongDate, dateFormatter,changeTypeMap,overRulesDetailMap,restTimeMap,computingModeMap);
      String overtime_type = splitBean.getOvertime_type();
      String changeType_key = belongDate + "_" + changeType;
      if(!overRulesDetailMap.containsKey(changeType_key)){
        return;
      }
      KQOvertimeRulesDetailEntity kqOvertimeRulesDetailEntity = overRulesDetailMap.get(changeType_key);
      int paidLeaveEnable = kqOverTimeRuleCalBiz.getPaidLeaveEnable(kqOvertimeRulesDetailEntity,overtime_type);
      boolean needSplitByTime = false;//kqOverTimeRuleCalBiz.getNeedSplitByTime(kqOvertimeRulesDetailEntity,paidLeaveEnable);

      int unit = KQOvertimeRulesBiz.getMinimumUnit();
      if(needSplitByTime){
        List<OvertimeBalanceTimeBean> overtimeBalanceTimeBeans = kqOverTimeRuleCalBiz.getOvertimeBalanceTimeBean(resourceId, fromdate, fromtime, todate, totime, changeTypeMap, kqOvertimeRulesDetailEntity, splitBean, restTimeMap,
            overRulesDetailMap);
        if(overtimeBalanceTimeBeans != null && !overtimeBalanceTimeBeans.isEmpty()){
          for(int i = 0 ; i < overtimeBalanceTimeBeans.size() ;i++){
            OvertimeBalanceTimeBean overtimeBalanceTimeBean = overtimeBalanceTimeBeans.get(i);
            String timepoint = overtimeBalanceTimeBean.getTimepoint();
            boolean needTX = overtimeBalanceTimeBean.isNeedTX();
            String tiaoxiu_id = "";
            String overtime_tiaoxiu_id = "";
            if(needTX){
              String split_key = splitBean.getRequestId()+"_"+splitBean.getDataId()+"_"+splitBean.getDetailId()+"_"
                  +splitBean.getFromDate()+"_"+splitBean.getFromTime()+"_"+splitBean.getToDate()+"_"+
                  splitBean.getToTime()+"_"+splitBean.getD_Mins()+"_"+timepoint;;
              String check_tiaoxiu_sql = "select * from kq_overtime_tiaoxiu where split_key=? ";
              rs1.executeQuery(check_tiaoxiu_sql, split_key);
              if(rs1.next()){
                overtime_tiaoxiu_id = rs1.getString("id");
                tiaoxiu_id = rs1.getString("tiaoxiu_id");
              }
            }
            int timepoint_mins = overtimeBalanceTimeBean.getTimepoint_mins();
            if(timepoint_mins == 0){
              continue;
            }
            String timePointStart = overtimeBalanceTimeBean.getTimepoint_start();
            String timePointEnd = overtimeBalanceTimeBean.getTimepoint_end();
            int timePointStart_index = kqTimesArrayComInfo.getArrayindexByTimes(timePointStart);
            int timePointEnd_index = kqTimesArrayComInfo.getArrayindexByTimes(timePointEnd);
            String pointFromtime = timePointStart+":00";
            String pointTotime = timePointEnd + ":00";
            String flow_overtime_sql = "insert into kq_flow_overtime(requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,fromdatedb,fromtimedb,todatedb,totimedb,tiaoxiuid)"+
                " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
            boolean isUp = rs.executeUpdate(flow_overtime_sql, requestId,resourceId,fromdate,pointFromtime,todate,pointTotime,timepoint_mins,"",belongDate,"",
                unit,changeType,paidLeaveEnable,"1",fromdateDB,fromtimedb,todatedb,totimedb,tiaoxiu_id);
            if(!isUp){
              kqLog.info("doComputingMode1 加班数据flow_overtime_sql记录失败！！！");
            }else{
              kqLog.info("doComputingMode1:flow_overtime_sql: "+flow_overtime_sql);
              kqLog.info("doComputingMode1:requestId:"+requestId+":resourceId:"+resourceId+":fromdate:"+fromdate+":fromtime:"+fromtime
                  +":todate:"+todate+":totime:"+totime+":D_Mins:"+D_Mins+":belongDate:"+belongDate+":unit:"+unit+":changeType:"+changeType
                  +":paidLeaveEnable:"+paidLeaveEnable+":fromdateDB:"+fromdateDB+":fromtimedb:"+fromtimedb+":todatedb:"+todatedb+":totimedb:"+totimedb);
            }
            if(overtime_tiaoxiu_id.length() > 0 && Util.getIntValue(overtime_tiaoxiu_id) > 0){
              String delSql = "delete from kq_overtime_tiaoxiu where id = ? ";
              rs1.executeUpdate(delSql, overtime_tiaoxiu_id);
            }
          }
        }else{
          String tiaoxiu_id = "";
          String overtime_tiaoxiu_id = "";
          String split_key = splitBean.getRequestId()+"_"+splitBean.getDataId()+"_"+splitBean.getDetailId()+"_"
              +splitBean.getFromDate()+"_"+splitBean.getFromTime()+"_"+splitBean.getToDate()+"_"+
              splitBean.getToTime()+"_"+splitBean.getD_Mins();
          String check_tiaoxiu_sql = "select * from kq_overtime_tiaoxiu where split_key=? ";
          rs1.executeQuery(check_tiaoxiu_sql, split_key);
          if(rs1.next()){
            overtime_tiaoxiu_id = rs1.getString("id");
            tiaoxiu_id = rs1.getString("tiaoxiu_id");
          }

          fromtime = fromtime+":00";
          totime = totime + ":00";
          String flow_overtime_sql = "insert into kq_flow_overtime(requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,fromdatedb,fromtimedb,todatedb,totimedb,tiaoxiuid)"+
              " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
          boolean isUp = rs.executeUpdate(flow_overtime_sql, requestId,resourceId,fromdate,fromtime,todate,totime,D_Mins,"",belongDate,"",
              unit,changeType,paidLeaveEnable,"1",fromdateDB,fromtimedb,todatedb,totimedb,tiaoxiu_id);
          if(!isUp){
            kqLog.info("doComputingMode1 加班数据flow_overtime_sql记录失败！！！");
          }else{
            kqLog.info("doComputingMode1:flow_overtime_sql: "+flow_overtime_sql);
            kqLog.info("doComputingMode1:requestId:"+requestId+":resourceId:"+resourceId+":fromdate:"+fromdate+":fromtime:"+fromtime
                +":todate:"+todate+":totime:"+totime+":D_Mins:"+D_Mins+":belongDate:"+belongDate+":unit:"+unit+":changeType:"+changeType
                +":paidLeaveEnable:"+paidLeaveEnable+":fromdateDB:"+fromdateDB+":fromtimedb:"+fromtimedb+":todatedb:"+todatedb+":totimedb:"+totimedb);
          }
          if(overtime_tiaoxiu_id.length() > 0 && Util.getIntValue(overtime_tiaoxiu_id) > 0){
            String delSql = "delete from kq_overtime_tiaoxiu where id = ? ";
            rs1.executeUpdate(delSql, overtime_tiaoxiu_id);
          }
        }
      }else{

        String tiaoxiu_id = "";
        String overtime_tiaoxiu_id = "";

        String split_key = splitBean.getRequestId()+"_"+splitBean.getDataId()+"_"+splitBean.getDetailId()+"_"
            +splitBean.getFromDate()+"_"+splitBean.getFromTime()+"_"+splitBean.getToDate()+"_"+
            splitBean.getToTime()+"_"+splitBean.getD_Mins();
        String check_tiaoxiu_sql = "select * from kq_overtime_tiaoxiu where split_key=? ";
        rs1.executeQuery(check_tiaoxiu_sql, split_key);
        if(rs1.next()){
          overtime_tiaoxiu_id = rs1.getString("id");
          tiaoxiu_id = rs1.getString("tiaoxiu_id");
        }

        fromtime = fromtime+":00";
        totime = totime + ":00";
        String flow_overtime_sql = "insert into kq_flow_overtime(requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,fromdatedb,fromtimedb,todatedb,totimedb,tiaoxiuid)"+
            " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
        boolean isUp = rs.executeUpdate(flow_overtime_sql, requestId,resourceId,fromdate,fromtime,todate,totime,D_Mins,"",belongDate,"",
            unit,changeType,paidLeaveEnable,"1",fromdateDB,fromtimedb,todatedb,totimedb,tiaoxiu_id);
        if(!isUp){
          kqLog.info("doComputingMode1 加班数据flow_overtime_sql记录失败！！！");
        }else{
          kqLog.info("doComputingMode1:flow_overtime_sql: "+flow_overtime_sql);
          kqLog.info("doComputingMode1:requestId:"+requestId+":resourceId:"+resourceId+":fromdate:"+fromdate+":fromtime:"+fromtime
              +":todate:"+todate+":totime:"+totime+":D_Mins:"+D_Mins+":belongDate:"+belongDate+":unit:"+unit+":changeType:"+changeType
              +":paidLeaveEnable:"+paidLeaveEnable+":fromdateDB:"+fromdateDB+":fromtimedb:"+fromtimedb+":todatedb:"+todatedb+":totimedb:"+totimedb);
        }
        if(overtime_tiaoxiu_id.length() > 0 && Util.getIntValue(overtime_tiaoxiu_id) > 0){
          String delSql = "delete from kq_overtime_tiaoxiu where id = ? ";
          rs1.executeUpdate(delSql, overtime_tiaoxiu_id);
        }
      }

    }catch (Exception e){
      kqLog.info("加班生成数据报错:doComputingMode1_splitBean:");
      StringWriter errorsWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(errorsWriter));
      kqLog.info(errorsWriter.toString());
    }

  }

  /**
   * 签到签退 生成加班数据
   * @param taskBeans
   */
  public void handleOverTime(List<KQTaskBean> taskBeans) throws Exception{
    try{
      if(true){
        KQOverTimeRuleCalBiz kqOverTimeRuleCalBiz = new KQOverTimeRuleCalBiz();
        for(int i = 0 ;i < taskBeans.size() ; i++){
          KQTaskBean taskBean = taskBeans.get(i);
          String tasktype = taskBean.getTasktype();
          String eventtype = "";
          if("punchcard".equalsIgnoreCase(tasktype)){
            eventtype = "考勤打卡生成加班.";
          }
          kqOverTimeRuleCalBiz.buildOvertime(taskBean.getResourceId(), taskBean.getOvertime_fromdate(),taskBean.getOvertime_todate(),
              eventtype);
        }
      }else{
        handleOverTime_old(taskBeans);
      }
    }catch (Exception e){
      kqLog.info("加班生成数据报错:handleOverTime:");
      StringWriter errorsWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(errorsWriter));
      kqLog.info(errorsWriter.toString());
    }
  }

  public void handleOverTime_old(List<KQTaskBean> taskBeans) throws Exception{
    DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    int workMins = (int)(KQFlowUtil.getOneDayHour(DurationTypeEnum.OVERTIME, "")*60);
    kqLog.info("handleOverTime:start:"+(taskBeans != null ? JSON.toJSONString(taskBeans) : "is empty"));
    //防止bean里重复的
    List<KQTaskBean> repeat_taskBeans =  new ArrayList<>();
    for(int i = 0 ;i < taskBeans.size() ; i++){
      KQTaskBean taskBean = taskBeans.get(i);
      if(repeat_taskBeans.contains(taskBean)){
        continue;
      }else{
        repeat_taskBeans.add(taskBean);
      }
      taskBean.setWorkmins(workMins);
      String resourceId = taskBean.getResourceId();
      String taskDate = taskBean.getTaskDate();
      int overtimeEnable = KQOvertimeRulesBiz.getOvertimeEnable(resourceId, taskDate);

      //如果人再当前指定日期下未开启加班，直接跳过
      if(overtimeEnable != 1){
        kqLog.info("签到签退 生成加班数据 handleOverTimeBySign:resourceId："+resourceId+":taskDate:"+taskDate+"不允许加班");
        continue;
      }

      int computingMode = KQOvertimeRulesBiz.getComputingMode(resourceId, taskDate);
      kqLog.info("加班生成调休方式:resourceId:"+resourceId+":taskDate:"+taskDate+":computingMode:"+computingMode);
      if(computingMode == 1) {
//        需审批，以审批单为准
        doComputingMode1(taskBean,fullFormatter);
      }
      if(computingMode == 2){
//        需审批，以打卡为准，但是不能超过审批时长
        doComputingMode2(taskBean,fullFormatter);
      }
      if(computingMode == 3){
//        无需审批，根据打卡时间计算加班时长
        doComputingMode3(taskBean,fullFormatter);
      }
    }
  }

  /**
   * 流程为主的就在流程归档的时候处理下，不在队列里进行处理了
   * @param taskBean
   * @param fullFormatter
   * @throws Exception
   */
  @Deprecated
  private void doComputingMode1(KQTaskBean taskBean,
      DateTimeFormatter fullFormatter) throws Exception{
    SplitBean splitBean = taskBean.getSplitBean();
    kqLog.info("doComputingMode1:splitBean: start");
    if(splitBean != null){
      RecordSet rs = new RecordSet();
      int changeType = splitBean.getChangeType();
      String requestId = splitBean.getRequestId();
      String resourceId = splitBean.getResourceId();
      String belongDate = splitBean.getBelongDate();
      String duration = splitBean.getDuration();
      String durationrule = splitBean.getDurationrule();
      String fromdateDB = splitBean.getFromdatedb();
      String fromtimedb = splitBean.getFromtimedb();
      String todatedb = splitBean.getTodatedb();
      String totimedb = splitBean.getTotimedb();
      String fromdate = splitBean.getFromDate();
      String fromtime = splitBean.getFromTime();
      String todate = splitBean.getToDate();
      String totime = splitBean.getToTime();
      double D_Mins = splitBean.getD_Mins();

      int unit = KQOvertimeRulesBiz.getMinimumUnit();
      int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, belongDate);
      paidLeaveEnable = paidLeaveEnable == 1?1:0;

      fromtime = fromtime+":00";
      totime = totime + ":00";
      String flow_overtime_sql = "insert into kq_flow_overtime(requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,fromdatedb,fromtimedb,todatedb,totimedb)"+
          " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
      boolean isUp = rs.executeUpdate(flow_overtime_sql, requestId,resourceId,fromdate,fromtime,todate,totime,D_Mins,"",belongDate,"",
          unit,changeType,paidLeaveEnable,"1",fromdateDB,fromtimedb,todatedb,totimedb);
      if(!isUp){
        kqLog.info("doComputingMode1 加班数据flow_overtime_sql记录失败！！！");
      }else{
        kqLog.info("doComputingMode1:flow_overtime_sql: "+flow_overtime_sql);
        kqLog.info("doComputingMode1:requestId:"+requestId+":resourceId:"+resourceId+":fromdate:"+fromdate+":fromtime:"+fromtime
            +":todate:"+todate+":totime:"+totime+":D_Mins:"+D_Mins+":belongDate:"+belongDate+":unit:"+unit+":changeType:"+changeType
            +":paidLeaveEnable:"+paidLeaveEnable+":fromdateDB:"+fromdateDB+":fromtimedb:"+fromtimedb+":todatedb:"+todatedb+":totimedb:"+totimedb);
      }
    }
  }

  /**
   * 无需审批，以打卡为准，但是不能超过审批时长
   * @param taskBean
   * @param fullFormatter
   */
  private void doComputingMode2(KQTaskBean taskBean,
      DateTimeFormatter fullFormatter) throws Exception{
    String resourceId = taskBean.getResourceId();
    String taskDate = taskBean.getTaskDate();
    KQFlowDataBiz kqFlowDataBiz = new KQFlowDataBiz.FlowDataParamBuilder().belongDateParam(taskDate).resourceidParam(resourceId).build();
    Map<String,Object> flowMaps = new HashMap<>();
    List<SplitBean> splitBeans = kqFlowDataBiz.getOverTimeData(flowMaps);
    if(!splitBeans.isEmpty()){
      Map<String,String> flowMap = new HashMap<>();
      Map<String,String> requestMap = new HashMap<>();
      for(int i = 0 ; i < splitBeans.size() ; i ++){
        SplitBean splitBean = splitBeans.get(i);
        String changeTpe = ""+splitBean.getChangeType();
        String requestid = splitBean.getRequestId();
        String flowMins = Util.null2String(splitBean.getD_Mins());
        if(flowMap.get(changeTpe) != null){
          double tmpMins = Util.getDoubleValue(Util.null2String(flowMap.get(changeTpe)),0.0);
          flowMap.put(changeTpe, ""+(tmpMins+Util.getDoubleValue(flowMins)));
        }else{
          flowMap.put(changeTpe, flowMins);
        }
        if(requestMap.get(changeTpe) != null){
          String tmpRequestid = Util.null2String(requestMap.get(changeTpe));
          if((","+tmpRequestid+",").indexOf(requestid) < 0){
            requestMap.put(changeTpe, tmpRequestid+","+requestid);
          }
        }else{
          requestMap.put(changeTpe, requestid);
        }
      }
      int changeType = KQOvertimeRulesBiz.getChangeType(resourceId, taskDate);
      if(requestMap.get(""+changeType) != null){
        taskBean.setRequestId(requestMap.get(""+changeType));
      }
      kqLog.info("签到签退 生成加班数据为 doComputingMode2:加班流程数据为 flowMap:"+flowMap);
//        1-节假日、2-工作日、3-休息日
      if(changeType == 1){
        doMode2ChangeType1(taskBean,fullFormatter,flowMap.get("1"));
      }
      if(changeType == 2){
        doMode2ChangeType2(taskBean,fullFormatter,flowMap.get("2"));
      }
      if(changeType == 3){
        doMode2ChangeType3(taskBean,fullFormatter,flowMap.get("3"));
      }
    }else{
      kqLog.info("签到签退 生成加班数据为 doComputingMode2:加班流程数据为空:resourceId:"+resourceId+":taskDate:"+taskDate);
    }
  }


  /**
   * 需审批，以打卡为准
   * 休息日
   * @param taskBean
   * @param fullFormatter
   * @param flowMins
   */
  private void doMode2ChangeType3(KQTaskBean taskBean, DateTimeFormatter fullFormatter,
      String flowMins) throws Exception{
    String taskDate = taskBean.getTaskDate();
    String resourceId = taskBean.getResourceId();
    String taskSignTime = taskBean.getTaskSignTime();
    String signInTime4Out = taskBean.getSignInTime4Out();
    String requestid = taskBean.getRequestId();
    String signDate = taskBean.getSignDate();
    String signEndDate= taskBean.getSignEndDate();
    String timesource = Util.null2String(taskBean.getTimesource());
    boolean isBefore = false;
    if("before".equalsIgnoreCase(timesource)){
      isBefore = true;
    }
    if(taskSignTime.length() > 0 && signInTime4Out.length() > 0){

      long mins = calNonWorkDuration(signInTime4Out,taskSignTime,taskDate,resourceId);
      if(isBefore && signEndDate.compareTo(signDate) < 0){
        //打卡日期和归属日期不是同一天的话
        String fromDateTime = signEndDate+" "+signInTime4Out;
        String toDateTime = signDate+" "+taskSignTime;
//        还需要交换一下开始日期和结束日期
        String tmpDate = signDate;
        signDate = signEndDate;
        signEndDate = tmpDate;
        mins = Duration.between(LocalDateTime.parse(fromDateTime, fullFormatter), LocalDateTime.parse(toDateTime, fullFormatter)).toMinutes();
      }
      if(mins > 0){
        int unit = KQOvertimeRulesBiz.getMinimumUnit();
        int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, taskDate);
        int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceId, taskDate);
        paidLeaveEnable = paidLeaveEnable == 1?1:0;
        if(mins >= minimumUnit && Util.getDoubleValue(flowMins) >= minimumUnit){
          RecordSet rs = new RecordSet();
          String fromtime = "";
          String totime = "";
          double D_flowMins = Util.getDoubleValue(flowMins);
          String tmp_taskSignTime = signEndDate+" "+taskSignTime;
          Map<String,Object> mutiMap = checkMultiSign(resourceId,taskDate,tmp_taskSignTime,fullFormatter,timesource);
          String hasSign  = Util.null2String(mutiMap.get("hasSign"));
          Map<String,Map<String,String>> sourceMap = (Map<String, Map<String, String>>) mutiMap.get("sourceMap");
          String before_checkLastSignTime  = "";
          String before_checkLastSignDate  = "";
          double before_hasMins = 0.0;

          String after_checkLastSignTime  = "";
          String after_checkLastSignDate  = "";
          double after_hasMins = 0.0;
          if(sourceMap.containsKey("before")){
            Map<String,String> tmpMap = sourceMap.get("before");
            before_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            before_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            before_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          if(sourceMap.containsKey("after")){
            Map<String,String> tmpMap = sourceMap.get("after");
            after_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            after_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            after_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          long hasMins  = Long.parseLong(Util.null2String(mutiMap.get("hasMins")));

          if("1".equalsIgnoreCase(hasSign)){
            String checkLastSignTime = "";
            if(isBefore){
              checkLastSignTime = before_checkLastSignTime;
            }else{
              checkLastSignTime = after_checkLastSignTime;
            }
            if(checkLastSignTime.compareTo(signInTime4Out) > 0){
              fromtime = checkLastSignTime;
            }else{
              fromtime = signInTime4Out;
            }
            totime = taskSignTime;
            if(hasMins >= D_flowMins){
              //如果已经生成过的加班数据已经大于等于流程时长了，那么此次的加班时长就是0
              mins  = 0;
            }else{
              if(isBefore){
                //如果是上班前 当前的打卡时长+下班后已经生成过的加班时长，和流程总时长的比较
                double before_after_mins = mins+after_hasMins;
                if(before_after_mins > D_flowMins){
                  mins = (long) (D_flowMins-after_hasMins-before_hasMins);
                }else{
                  mins = (long) (mins-before_hasMins);
                }
              }else{
                //如果是下班后 当前的打卡时长+上班前已经生成过的加班时长，和流程总时长的比较
                double before_after_mins = mins+before_hasMins;
                if(before_after_mins > D_flowMins){
                  mins = (long) (D_flowMins-before_hasMins-after_hasMins);
                }else{
                  mins = (long) (mins-after_hasMins);
                }
              }
            }
          }else{
            fromtime = signInTime4Out;
            totime = taskSignTime;
            if(D_flowMins > 0 && mins > D_flowMins){
              //之前没有生成过加班 打卡加班时长不能超过流程时长
              mins  = (long) D_flowMins;
            }
          }
          mins = mins < 0 ? 0 : mins;
          if(mins <= 0){
            return;
          }

          String tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,taskDate,mins+"","0","",requestid,"",taskDate,null);
          if(Util.getIntValue(tiaoxiuId) > 0){
            kqLog.info("生成加班数据成功 doMode2ChangeType3:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }else{
            kqLog.info("生成加班数据失败 doMode2ChangeType3:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }
          String uuid = UUID.randomUUID().toString();
          String flow_overtime_sql = "insert into kq_flow_overtime (requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,tiaoxiuId,uuid)"+
              " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
          boolean isUp = rs.executeUpdate(flow_overtime_sql, requestid,resourceId,signDate,fromtime,signEndDate,totime,mins,"",taskDate,"",unit,"3",paidLeaveEnable,"2",tiaoxiuId,uuid);
          if(!isUp){
            kqLog.info("doMode2ChangeType3 加班数据记录失败！！！");
          }

          logMultiSign(resourceId,taskDate,taskSignTime,"",0,signInTime4Out,mins,signEndDate,timesource,
              uuid, tiaoxiuId);
        }else{
          kqLog.info("签到签退 生成加班数据为 doMode2ChangeType2:实际加班时长mins:"+mins+":最小加班时长minimumUnit:"+minimumUnit+":流程时长:Util.getDoubleValue(flowMins):"+Util.getDoubleValue(flowMins));
        }

      }else{
        kqLog.info("签到签退 生成加班数据为 doMode2ChangeType3:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
      }
    }else{
      kqLog.info("签到签退 生成加班数据异常 doMode2ChangeType3:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
    }

  }

  /**
   * 需审批，以打卡为准
   * 工作日
   * @param taskBean
   * @param fullFormatter
   * @param flowMins
   */
  private void doMode2ChangeType2(KQTaskBean taskBean, DateTimeFormatter fullFormatter,
      String flowMins) throws Exception{
    String taskDate = taskBean.getTaskDate();
    String resourceId = taskBean.getResourceId();
    String taskSignTime = taskBean.getTaskSignTime();
    String lastWorkTime = taskBean.getLastWorkTime();
    String signDate = taskBean.getSignDate();
    String signEndDate = taskBean.getSignEndDate();
    String requestid = taskBean.getRequestId();
    String timesource = Util.null2String(taskBean.getTimesource());
    boolean isBefore = false;
    if("before".equalsIgnoreCase(timesource)){
      isBefore = true;
    }
    int workMins = taskBean.getWorkmins();
    if(taskSignTime.length() > 0 && lastWorkTime.length() > 0){
      if(lastWorkTime.length() == 5){
        lastWorkTime = lastWorkTime+":00";
      }
      String fromDateTime = signDate+" "+lastWorkTime;
      String toDateTime = signEndDate+" "+taskSignTime;
      int startTime = KQOvertimeRulesBiz.getStartTime(resourceId, taskDate);
      startTime = startTime < 0 ? 0 : startTime;
      if(isBefore){
        //上班前就不不需要考虑下班后多久算加班这个逻辑了
        startTime = 0;
      }
      long mins = Duration.between(LocalDateTime.parse(fromDateTime, fullFormatter).plusMinutes(startTime), LocalDateTime.parse(toDateTime, fullFormatter)).toMinutes();
      if(mins > 0){
        String workingHours = Util.null2String(workMins/60.0);
        int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceId, taskDate);
        int unit = KQOvertimeRulesBiz.getMinimumUnit();
        int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, taskDate);
        paidLeaveEnable = paidLeaveEnable == 1?1:0;
        if(mins >= minimumUnit && Util.getDoubleValue(flowMins) >= minimumUnit){
          RecordSet rs = new RecordSet();
          boolean isLateoutlatein = checkIsLateoutlatein(resourceId, taskDate);
          if(!isLateoutlatein){
            String fromDate = "";
            String fromtime = "";
            String toDate = "";
            String totime = "";
            double D_flowMins = Util.getDoubleValue(flowMins);
            String tmp_taskSignTime = signEndDate+" "+taskSignTime;
            Map<String,Object> mutiMap = checkMultiSign(resourceId,taskDate,tmp_taskSignTime,fullFormatter,timesource);
            String hasSign  = Util.null2String(mutiMap.get("hasSign"));
            Map<String,Map<String,String>> sourceMap = (Map<String, Map<String, String>>) mutiMap.get("sourceMap");
            String before_checkLastSignTime  = "";
            String before_checkLastSignDate  = "";
            String before_checkLastSignTimesource  = "";
            double before_hasMins = 0.0;

            String after_checkLastSignTime  = "";
            String after_checkLastSignDate  = "";
            String after_checkLastSignTimesource  = "";
            double after_hasMins = 0.0;
            if(sourceMap.containsKey("before")){
              Map<String,String> tmpMap = sourceMap.get("before");
              before_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
              before_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
              before_checkLastSignTimesource = Util.null2String(tmpMap.get("checkLastSignTimesource"));
              before_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
            }
            if(sourceMap.containsKey("after")){
              Map<String,String> tmpMap = sourceMap.get("after");
              after_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
              after_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
              after_checkLastSignTimesource = Util.null2String(tmpMap.get("checkLastSignTimesource"));
              after_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
            }

            long hasMins  = Long.parseLong(Util.null2String(mutiMap.get("hasMins")));
            if("1".equalsIgnoreCase(hasSign)){
              String checkLastSignTime = "";
              String checkLastSignDate = "";
              String checkLastSignTimesource = "";
              if(isBefore){
                checkLastSignTime = before_checkLastSignTime;
                checkLastSignDate = before_checkLastSignDate;
                checkLastSignTimesource = before_checkLastSignTimesource;
              }else{
                checkLastSignTime = after_checkLastSignTime;
                checkLastSignDate = after_checkLastSignDate;
                checkLastSignTimesource = after_checkLastSignTimesource;
              }
              if(isBefore){
                fromDate = signDate;
                fromtime = lastWorkTime;
                if("before".equalsIgnoreCase(checkLastSignTimesource)){
                  toDate = checkLastSignDate;
                  totime = checkLastSignTime;
                }else{
                  String[] toDateTimes = toDateTime.split(" ");
                  if(toDateTimes.length == 2){
                    toDate = toDateTimes[0];
                    totime = toDateTimes[1];
                  }
                }
              }else{
                if(checkLastSignDate.length() > 0 && checkLastSignTime.length() > 0){
                  fromDate = checkLastSignDate;
                  fromtime = checkLastSignTime;
                }else{
                  fromDate = signDate;
                  fromtime = lastWorkTime;
                }
                toDate = signEndDate;
                totime = taskSignTime;
              }

              if(hasMins >= D_flowMins){
                //如果已经生成过的加班数据已经大于等于流程时长了，那么此次的加班时长就是0
                mins  = 0;
              }else{
                if(isBefore){
                  //如果是上班前 当前的打卡时长+下班后已经生成过的加班时长，和流程总时长的比较
                  double before_after_mins = mins+after_hasMins;
                  if(before_after_mins > D_flowMins){
                    mins = (long) (D_flowMins-after_hasMins-before_hasMins);
                  }else{
                    mins = (long) (mins-before_hasMins);
                  }
                }else{
                  //如果是下班后 当前的打卡时长+上班前已经生成过的加班时长，和流程总时长的比较
                  double before_after_mins = mins+before_hasMins;
                  if(before_after_mins > D_flowMins){
                    mins = (long) (D_flowMins-before_hasMins-after_hasMins);
                  }else{
                    mins = (long) (mins-after_hasMins);
                  }
                }
              }
            }else{
              String[] fromDateTimes = fromDateTime.split(" ");
              String[] toDateTimes = toDateTime.split(" ");
              if(fromDateTimes.length == 2){
                fromDate = fromDateTimes[0];
                fromtime = fromDateTimes[1];
              }
              if(toDateTimes.length == 2){
                toDate = toDateTimes[0];
                totime = toDateTimes[1];
              }
              if(D_flowMins > 0 && mins > D_flowMins){
                //之前没有生成过加班 打卡加班时长不能超过流程时长
                mins  = (long) D_flowMins;
              }
            }
            mins = mins < 0 ? 0 : mins;
            if(mins <= 0){
              return;
            }

            String tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,taskDate,mins+"","0",workingHours,requestid,"",taskDate,null);
            if(Util.getIntValue(tiaoxiuId) > 0){
              kqLog.info("生成加班数据为成功 doMode2ChangeType2:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins+":workingHours:"+workingHours);
            }else{
              kqLog.info("生成加班数据为失败 doMode2ChangeType2:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins+":workingHours:"+workingHours);
            }
            String uuid = UUID.randomUUID().toString();
            String flow_overtime_sql = "insert into kq_flow_overtime (requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,tiaoxiuId,uuid)"+
                " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
            boolean isUp = rs.executeUpdate(flow_overtime_sql, requestid,resourceId,signDate,fromtime,signEndDate,totime,mins,"",taskDate,"",unit,"2",paidLeaveEnable,"2",tiaoxiuId,uuid);
            if(!isUp){
              kqLog.info("doMode2ChangeType2 加班数据记录失败！！！");
            }

            logMultiSign(resourceId,taskDate,taskSignTime,lastWorkTime,0,"",mins,signEndDate,timesource,
                uuid, tiaoxiuId);
          }
        }else{
          kqLog.info("签到签退 生成加班数据为 doMode2ChangeType2:实际加班时长mins:"+mins+":最小加班时长minimumUnit:"+minimumUnit+":流程时长:Util.getDoubleValue(flowMins):"+Util.getDoubleValue(flowMins));
        }
      }else{
        kqLog.info("签到签退 生成加班数据为 doMode2ChangeType2:fromDateTime:"+fromDateTime+":toDateTime:"+toDateTime+":mins:"+mins);
      }
    }else{
      kqLog.info("签到签退 生成加班数据异常 doMode2ChangeType2:taskSignTime:"+taskSignTime+":lastWorkTime:"+lastWorkTime);
    }

  }

  /**
   * 需审批，以打卡为准
   * 节假日
   * @param taskBean
   * @param fullFormatter
   * @param flowMins
   */
  private void doMode2ChangeType1(KQTaskBean taskBean, DateTimeFormatter fullFormatter,
      String flowMins) throws Exception{
    String taskDate = taskBean.getTaskDate();
    String resourceId = taskBean.getResourceId();
    String taskSignTime = taskBean.getTaskSignTime();
    String signInTime4Out = taskBean.getSignInTime4Out();
    String requestid = taskBean.getRequestId();
    String signDate = taskBean.getSignDate();
    String signEndDate= taskBean.getSignEndDate();
    String timesource = Util.null2String(taskBean.getTimesource());
    boolean isBefore = false;
    if("before".equalsIgnoreCase(timesource)){
      isBefore = true;
    }
    if(taskSignTime.length() > 0 && signInTime4Out.length() > 0){

      long mins = calNonWorkDuration(signInTime4Out,taskSignTime,taskDate,resourceId);
      if(isBefore && signEndDate.compareTo(signDate) < 0){
        //打卡日期和归属日期不是同一天的话
        String fromDateTime = signEndDate+" "+signInTime4Out;
        String toDateTime = signDate+" "+taskSignTime;
//        还需要交换一下开始日期和结束日期
        String tmpDate = signDate;
        signDate = signEndDate;
        signEndDate = tmpDate;
        mins = Duration.between(LocalDateTime.parse(fromDateTime, fullFormatter), LocalDateTime.parse(toDateTime, fullFormatter)).toMinutes();
      }
      if(mins > 0){
        int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceId, taskDate);
        int unit = KQOvertimeRulesBiz.getMinimumUnit();
        int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, taskDate);
        paidLeaveEnable = paidLeaveEnable == 1?1:0;
        if(mins >= minimumUnit && Util.getDoubleValue(flowMins) >= minimumUnit){
          RecordSet rs = new RecordSet();
          String fromtime = "";
          String totime = "";
          double D_flowMins = Util.getDoubleValue(flowMins);
          String tmp_taskSignTime = signEndDate+" "+taskSignTime;
          Map<String,Object> mutiMap = checkMultiSign(resourceId,taskDate,tmp_taskSignTime,fullFormatter,timesource);
          String hasSign  = Util.null2String(mutiMap.get("hasSign"));
          Map<String,Map<String,String>> sourceMap = (Map<String, Map<String, String>>) mutiMap.get("sourceMap");
          String before_checkLastSignTime  = "";
          String before_checkLastSignDate  = "";
          double before_hasMins = 0.0;

          String after_checkLastSignTime  = "";
          String after_checkLastSignDate  = "";
          double after_hasMins = 0.0;
          if(sourceMap.containsKey("before")){
            Map<String,String> tmpMap = sourceMap.get("before");
            before_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            before_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            before_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          if(sourceMap.containsKey("after")){
            Map<String,String> tmpMap = sourceMap.get("after");
            after_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            after_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            after_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          long hasMins  = Long.parseLong(Util.null2String(mutiMap.get("hasMins")));
          if("1".equalsIgnoreCase(hasSign)){
            String checkLastSignTime = "";
            if(isBefore){
              checkLastSignTime = before_checkLastSignTime;
            }else{
              checkLastSignTime = after_checkLastSignTime;
            }
            if(checkLastSignTime.compareTo(signInTime4Out) > 0){
              fromtime = checkLastSignTime;
            }else{
              fromtime = signInTime4Out;
            }
            totime = taskSignTime;
            if(hasMins >= D_flowMins){
              //如果已经生成过的加班数据已经大于等于流程时长了，那么此次的加班时长就是0
              mins  = 0;
            }else{
              if(mins > D_flowMins){
                //如果打卡时长大于流程时长
                mins = (long) (D_flowMins-hasMins);
              }else {
                if(isBefore){
                  //如果是上班前 当前的打卡时长+下班后已经生成过的加班时长，和流程总时长的比较
                  double before_after_mins = mins+after_hasMins;
                  if(before_after_mins > D_flowMins){
                    mins = (long) (D_flowMins-after_hasMins-before_hasMins);
                  }else{
                    mins = (long) (mins-before_hasMins);
                  }
                }else{
                  //如果是下班后 当前的打卡时长+上班前已经生成过的加班时长，和流程总时长的比较
                  double before_after_mins = mins+before_hasMins;
                  if(before_after_mins > D_flowMins){
                    mins = (long) (D_flowMins-before_hasMins-after_hasMins);
                  }else{
                    mins = (long) (mins-after_hasMins);
                  }
                }
              }
            }
          }else{
            fromtime = signInTime4Out;
            totime = taskSignTime;
            if(D_flowMins > 0 && mins > D_flowMins){
              //之前没有生成过加班 打卡加班时长不能超过流程时长
              mins  = (long) D_flowMins;
            }
          }
          mins = mins < 0 ? 0 : mins;
          if(mins <= 0){
            return;
          }

          String tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,taskDate,mins+"","0","",requestid,"",taskDate,null);
          if(Util.getIntValue(tiaoxiuId) > 0){
            kqLog.info("生成加班数据成功 doMode2ChangeType1:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }else{
            kqLog.info("生成加班数据失败 doMode2ChangeType1:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }

          String uuid = UUID.randomUUID().toString();
          String flow_overtime_sql = "insert into kq_flow_overtime (requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,tiaoxiuId,uuid)"+
              " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
          boolean isUp = rs.executeUpdate(flow_overtime_sql, requestid,resourceId,signDate,fromtime,signEndDate,totime,mins,"",taskDate,"",unit,"1",paidLeaveEnable,"2",tiaoxiuId,uuid);
          if(!isUp){
            kqLog.info("doMode2ChangeType1 加班数据记录失败！！！");
          }
          logMultiSign(resourceId,taskDate,taskSignTime,"",0,signInTime4Out,mins,signEndDate,timesource,
              uuid, tiaoxiuId);
        }else{
          kqLog.info("签到签退 生成加班数据为 doMode2ChangeType1:实际加班时长mins:"+mins+":最小加班时长minimumUnit:"+minimumUnit+":流程时长:Util.getDoubleValue(flowMins):"+Util.getDoubleValue(flowMins));
        }

      }else{
        kqLog.info("签到签退 生成加班数据为 doMode2ChangeType1:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
      }
    }else{
      kqLog.info("签到签退 生成加班数据异常 doMode2ChangeType1:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
    }

  }

  /**
   * 打卡计算非工作时长
   * @param fromTime
   * @param toTime
   * @param date
   * @param resourceid
   * @return
   */
  private int calNonWorkDuration(String fromTime,String toTime,String date,String resourceid) {
    fromTime = (fromTime.length() > 6 ? fromTime.substring(0,5) : fromTime);
    toTime = (toTime.length() > 6 ? toTime.substring(0,5) : toTime);

    List<String[]> restTimeList = KQOvertimeRulesBiz.getRestTimeList(resourceid, date);
    KQTimesArrayComInfo arrayComInfo = new KQTimesArrayComInfo();
    int[] initArrays = arrayComInfo.getInitArr();
    int startIndex = 0;
    int endIndex = 0;
    startIndex = arrayComInfo.getArrayindexByTimes(fromTime);
    endIndex = arrayComInfo.getArrayindexByTimes(toTime);
    if(startIndex > endIndex){
      return 0;
    }

    //先把要计算的时长填充上0
    Arrays.fill(initArrays, startIndex, endIndex, 0);

    //再把休息时间填充上去
    if(!restTimeList.isEmpty()){
      for(int i =0 ; i < restTimeList.size() ; i++){
        String[] restTimes = restTimeList.get(i);
        if(restTimes.length == 2){
          int restStart = arrayComInfo.getArrayindexByTimes(restTimes[0]);
          int restEnd = arrayComInfo.getArrayindexByTimes(restTimes[1]);
          Arrays.fill(initArrays, restStart, restEnd, 1);
        }
      }
    }
    //最后排除掉休息时间还剩多少加班时长就是最终的结果
    int curMins = arrayComInfo.getCnt(initArrays, startIndex, endIndex+1, 0);

    return curMins;
  }

  /**
   * 无需审批，根据打卡时间计算加班时长
   * @param taskBean
   * @param fullFormatter
   */
  private void doComputingMode3(KQTaskBean taskBean,
      DateTimeFormatter fullFormatter) throws Exception{
    String resourceId = taskBean.getResourceId();
    String taskDate = taskBean.getTaskDate();
    int changeType = KQOvertimeRulesBiz.getChangeType(resourceId, taskDate);
//        1-节假日、2-工作日、3-休息日
    if(changeType == 1){
      doMode3ChangeType1(taskBean,fullFormatter);
    }
    if(changeType == 2){
      doMode3ChangeType2(taskBean,fullFormatter);
    }
    if(changeType == 3){
      doMode3ChangeType3(taskBean,fullFormatter);
    }
  }

  /**
   * 无需审批，根据打开时间计算加班时长
   * 休息日处理
   * @param taskBean
   * @param fullFormatter
   */
  private void doMode3ChangeType3(KQTaskBean taskBean,
      DateTimeFormatter fullFormatter) throws Exception{
    String taskDate = taskBean.getTaskDate();
    String resourceId = taskBean.getResourceId();
    String taskSignTime = taskBean.getTaskSignTime();
    String signInTime4Out = taskBean.getSignInTime4Out();
    String signDate = taskBean.getSignDate();
    String signEndDate= taskBean.getSignEndDate();
    String timesource = Util.null2String(taskBean.getTimesource());
    String up_tiaoxiuid = taskBean.getTiaoxiuId();
    boolean isBefore = false;
    if("before".equalsIgnoreCase(timesource)){
      isBefore = true;
    }
    if(taskSignTime.length() > 0 && signInTime4Out.length() > 0){

      long mins = calNonWorkDuration(signInTime4Out,taskSignTime,taskDate,resourceId);
      if(isBefore && signEndDate.compareTo(signDate) < 0){
        //打卡日期和归属日期不是同一天的话
        String fromDateTime = signEndDate+" "+signInTime4Out;
        String toDateTime = signDate+" "+taskSignTime;
//        还需要交换一下开始日期和结束日期
        String tmpDate = signDate;
        signDate = signEndDate;
        signEndDate = tmpDate;
        mins = Duration.between(LocalDateTime.parse(fromDateTime, fullFormatter), LocalDateTime.parse(toDateTime, fullFormatter)).toMinutes();
      }
      if(mins > 0){
        RecordSet rs = new RecordSet();
        int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceId, taskDate);
        int unit = KQOvertimeRulesBiz.getMinimumUnit();
        int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, taskDate);
        paidLeaveEnable = paidLeaveEnable == 1?1:0;
        if(mins >= minimumUnit){
          if(taskSignTime.length() == 5){
            taskSignTime += ":00";
          }
          String tmp_taskSignTime = signEndDate+" "+taskSignTime;
          Map<String,Object> mutiMap = checkMultiSign(resourceId,taskDate,tmp_taskSignTime,fullFormatter,timesource);
          String hasSign  = Util.null2String(mutiMap.get("hasSign"));
          Map<String,Map<String,String>> sourceMap = (Map<String, Map<String, String>>) mutiMap.get("sourceMap");
          String before_checkLastSignTime  = "";
          String before_checkLastSignDate  = "";
          double before_hasMins = 0.0;

          String after_checkLastSignTime  = "";
          String after_checkLastSignDate  = "";
          double after_hasMins = 0.0;
          if(sourceMap.containsKey("before")){
            Map<String,String> tmpMap = sourceMap.get("before");
            before_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            before_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            before_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          if(sourceMap.containsKey("after")){
            Map<String,String> tmpMap = sourceMap.get("after");
            after_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            after_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            after_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          long hasMins  = Long.parseLong(Util.null2String(mutiMap.get("hasMins")));

          String fromtime = "";
          String totime = "";
          if("1".equalsIgnoreCase(hasSign)){
            String checkLastSignTime = "";
            if(isBefore){
              checkLastSignTime = before_checkLastSignTime;
            }else{
              checkLastSignTime = after_checkLastSignTime;
            }
            if(checkLastSignTime.compareTo(signInTime4Out) > 0){
              fromtime = checkLastSignTime;
            }else{
              fromtime = signInTime4Out;
            }
            totime = taskSignTime;
            if(isBefore){
              mins  = (long) (mins-before_hasMins);
            }else{
              mins  = (long) (mins-after_hasMins);
            }
          }else{
            fromtime = signInTime4Out;
            totime = taskSignTime;
          }
          mins = mins < 0 ? 0 : mins;

          String tiaoxiuId = "";
          if(up_tiaoxiuid.length() > 0 && Util.getIntValue(up_tiaoxiuid) > 0){
            boolean is_tiaoxiuId = KQBalanceOfLeaveBiz.updateExtraAmountByDis5(up_tiaoxiuid,Util.getDoubleValue(mins+""), "");
            tiaoxiuId = up_tiaoxiuid;
          }else{
            tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,taskDate,mins+"","0","","","",taskDate,null);
          }
          if(Util.getIntValue(tiaoxiuId) > 0){
            kqLog.info("生成加班数据成功 doMode3ChangeType3:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }else{
            kqLog.info("生成加班数据失败 doMode3ChangeType3:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }

          String uuid = UUID.randomUUID().toString();
          String flow_overtime_sql = "insert into kq_flow_overtime (requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,tiaoxiuId,uuid)"+
              " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
          boolean isUp = rs.executeUpdate(flow_overtime_sql, "",resourceId,signDate,fromtime,signEndDate,totime,mins,"",taskDate,"",unit,"3",paidLeaveEnable,"3",tiaoxiuId,uuid);
          if(!isUp){
            kqLog.info("doMode2ChangeType1 加班数据记录失败！！！");
          }
          logMultiSign(resourceId,taskDate,taskSignTime,"",0,signInTime4Out,mins,signEndDate,timesource,
              uuid, tiaoxiuId);

        }else{
          kqLog.info("签到签退 生成加班数据为 doMode3ChangeType3:实际加班时长mins:"+mins+":最小加班时长minimumUnit:"+minimumUnit);
        }

      }else{
        kqLog.info("签到签退 生成加班数据为 doMode3ChangeType3:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
      }
    }else{
      kqLog.info("签到签退 生成加班数据异常 doMode3ChangeType3:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
    }

  }


  /**
   * 无需审批，根据打开时间计算加班时长
   * 工作日处理
   * @param taskBean
   * @param fullFormatter
   */
  private void doMode3ChangeType2(KQTaskBean taskBean,
      DateTimeFormatter fullFormatter) throws Exception{
    String taskDate = taskBean.getTaskDate();
    String resourceId = taskBean.getResourceId();
    String taskSignTime = taskBean.getTaskSignTime();
    String lastWorkTime = taskBean.getLastWorkTime();
    String signDate = taskBean.getSignDate();
    String signEndDate = taskBean.getSignEndDate();
    String timesource = taskBean.getTimesource();
    String up_tiaoxiuid = taskBean.getTiaoxiuId();
    boolean isBefore = false;
    if("before".equalsIgnoreCase(timesource)){
      isBefore = true;
    }
    int workMins = taskBean.getWorkmins();
    if(taskSignTime.length() > 0 && lastWorkTime.length() > 0){
      if(lastWorkTime.length() == 5){
        lastWorkTime = lastWorkTime+":00";
      }
      String fromDateTime = signDate+" "+lastWorkTime;
      String toDateTime = signEndDate+" "+taskSignTime;
      int startTime = KQOvertimeRulesBiz.getStartTime(resourceId, taskDate);
      startTime = startTime < 0 ? 0 : startTime;
      if(isBefore){
        //上班前就不不需要考虑下班后多久算加班这个逻辑了
        startTime = 0;
      }
      long mins = Duration.between(LocalDateTime.parse(fromDateTime, fullFormatter).plusMinutes(startTime), LocalDateTime.parse(toDateTime, fullFormatter)).toMinutes();
      if(mins > 0){
        String workingHours = Util.null2String(workMins/60.0);
        int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceId, taskDate);
        int unit = KQOvertimeRulesBiz.getMinimumUnit();
        int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, taskDate);
        paidLeaveEnable = paidLeaveEnable == 1?1:0;
        if(mins >= minimumUnit){
          if(taskSignTime.length() == 5){
            taskSignTime += ":00";
          }
          RecordSet rs = new RecordSet();
          String tmp_taskSignTime = signEndDate+" "+taskSignTime;
          Map<String,Object> mutiMap = checkMultiSign(resourceId,taskDate,tmp_taskSignTime,fullFormatter,timesource);
          String hasSign  = Util.null2String(mutiMap.get("hasSign"));
          Map<String,Map<String,String>> sourceMap = (Map<String, Map<String, String>>) mutiMap.get("sourceMap");
          String before_checkLastSignTime  = "";
          String before_checkLastSignDate  = "";
          String before_checkLastSignTimesource  = "";
          double before_hasMins = 0.0;

          String after_checkLastSignTime  = "";
          String after_checkLastSignDate  = "";
          String after_checkLastSignTimesource  = "";
          double after_hasMins = 0.0;
          if(sourceMap.containsKey("before")){
            Map<String,String> tmpMap = sourceMap.get("before");
            before_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            before_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            before_checkLastSignTimesource = Util.null2String(tmpMap.get("checkLastSignTimesource"));
            before_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          if(sourceMap.containsKey("after")){
            Map<String,String> tmpMap = sourceMap.get("after");
            after_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            after_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            after_checkLastSignTimesource = Util.null2String(tmpMap.get("checkLastSignTimesource"));
            after_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          long hasMins  = Long.parseLong(Util.null2String(mutiMap.get("hasMins")));
          boolean isLateoutlatein = checkIsLateoutlatein(resourceId, taskDate);
          if(!isLateoutlatein){
            String fromDate = "";
            String fromtime = "";
            String toDate = "";
            String totime = "";
            if("1".equalsIgnoreCase(hasSign)){
              String checkLastSignTime = "";
              String checkLastSignDate = "";
              String checkLastSignTimesource = "";
              if(isBefore){
                checkLastSignTime = before_checkLastSignTime;
                checkLastSignDate = before_checkLastSignDate;
                checkLastSignTimesource = before_checkLastSignTimesource;
              }else{
                checkLastSignTime = after_checkLastSignTime;
                checkLastSignDate = after_checkLastSignDate;
                checkLastSignTimesource = after_checkLastSignTimesource;
              }
              if(isBefore){
                fromDate = signDate;
                fromtime = lastWorkTime;
                if("before".equalsIgnoreCase(checkLastSignTimesource)){
                  toDate = checkLastSignDate;
                  totime = checkLastSignTime;
                }else{
                  String[] toDateTimes = toDateTime.split(" ");
                  if(toDateTimes.length == 2){
                    toDate = toDateTimes[0];
                    totime = toDateTimes[1];
                  }
                }
                mins  = (long) (mins-before_hasMins);
              }else{
                if(checkLastSignDate.length() > 0 && checkLastSignTime.length() > 0) {
                  fromDate = checkLastSignDate;
                  fromtime = checkLastSignTime;
                }else{
                  fromDate = signDate;
                  fromtime = lastWorkTime;
                }
                toDate = signEndDate;
                totime = taskSignTime;
                mins  = (long) (mins-after_hasMins);
              }
            }else{
              String[] fromDateTimes = fromDateTime.split(" ");
              String[] toDateTimes = toDateTime.split(" ");
              if(fromDateTimes.length == 2){
                fromDate = fromDateTimes[0];
                fromtime = fromDateTimes[1];
              }
              if(toDateTimes.length == 2){
                toDate = toDateTimes[0];
                totime = toDateTimes[1];
              }
            }
            mins = mins < 0 ? 0 : mins;

            String tiaoxiuId = "";
            if(up_tiaoxiuid.length() > 0 && Util.getIntValue(up_tiaoxiuid) > 0){
              boolean is_tiaoxiuId = KQBalanceOfLeaveBiz.updateExtraAmountByDis5(up_tiaoxiuid,Util.getDoubleValue(mins+""), "");
              tiaoxiuId = up_tiaoxiuid;
            }else{
              tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,taskDate,mins+"","0","","","",taskDate,null);
            }
            if(Util.getIntValue(tiaoxiuId) > 0){
              kqLog.info("生成加班数据成功  doMode3ChangeType2:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins+":workingHours:"+workingHours);
            }else{
              kqLog.info("生成加班数据失败 doMode3ChangeType2:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins+":workingHours:"+workingHours);
            }

            String uuid = UUID.randomUUID().toString();
            String flow_overtime_sql = "insert into kq_flow_overtime (requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,tiaoxiuId,uuid)"+
                " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
            boolean isUp = rs.executeUpdate(flow_overtime_sql, "",resourceId,fromDate,fromtime,toDate,totime,mins,"",taskDate,"",unit,"2",paidLeaveEnable,"3",tiaoxiuId,uuid);
            if(!isUp){
              kqLog.info("doMode2ChangeType1 加班数据记录失败！！！");
            }
            logMultiSign(resourceId,taskDate,taskSignTime,lastWorkTime,workMins,"",mins,signEndDate,timesource,
                uuid, tiaoxiuId);
          }
        }else{
          kqLog.info("签到签退 生成加班数据为 doMode3ChangeType2:实际加班时长mins:"+mins+":最小加班时长minimumUnit:"+minimumUnit);
        }
      }else{
        kqLog.info("签到签退 生成加班数据为 doMode3ChangeType2:fromDateTime:"+fromDateTime+":toDateTime:"+toDateTime+":mins:"+mins);
      }
    }else{
      kqLog.info("签到签退 生成加班数据异常 doMode3ChangeType2:taskSignTime:"+taskSignTime+":lastWorkTime:"+lastWorkTime);
    }

  }

  /**
   * 校验多次签退问题
   * @param taskSignTime 最新的签退日期时间
   * @param fullFormatter
   */
  public Map<String,Object> checkMultiSign(String resourceId, String taskDate,String taskSignTime, DateTimeFormatter fullFormatter,String timesource) {
    RecordSet rs = new RecordSet();
    Map<String,Object> mutiMap = new HashMap<>();
    long mins = 0L;
    boolean hasSigned = false;
    //倒数第二新的签退时间
    String signDate = "";
    String checkLastSignTime = "";
    String checkLastSignTimesource = "";
    Map<String,Map<String,String>> sourceMap = new HashMap<>();
    Map<String,String> minsMap = new HashMap<>();
    Map<String,String> datetimeMap = new HashMap<>();
    //已经生成了的加班时长
    long hasMins = 0L;
    String checkSql = "select * from kq_overtime_signtask where resourceid=? and belongdate=?  order by signdate ,signtime  ";
    rs.executeQuery(checkSql,resourceId,taskDate);
    while(rs.next()){
      signDate = rs.getString("signdate");
      checkLastSignTime = rs.getString("signtime");
      checkLastSignTimesource = rs.getString("timesource");
      double tmp_mins = Util.getDoubleValue(rs.getString("mins"),0.0);
      hasMins += tmp_mins;
      if(sourceMap.containsKey(checkLastSignTimesource)){
        Map<String,String> tmp_minsMap = sourceMap.get(checkLastSignTimesource);
        String tmp_hasMins = tmp_minsMap.get("hasMins");
        if(checkLastSignTimesource.equalsIgnoreCase("after")){
          tmp_minsMap.put("checkLastSignDate", signDate);
          tmp_minsMap.put("checkLastSignTime", checkLastSignTime);
          tmp_minsMap.put("checkLastSignTimesource", checkLastSignTimesource);
        }else{
          //before 上班前的时候，第一条就是最早的打卡时间所以后面不需要更新了
        }
        double sourceMins = Util.getDoubleValue(tmp_hasMins,0.0);
        tmp_minsMap.put("hasMins", (tmp_mins+sourceMins)+"");
      }else{
        minsMap = new HashMap<>();
        minsMap.put("hasMins", tmp_mins+"");
        minsMap.put("checkLastSignDate", signDate+"");
        minsMap.put("checkLastSignTime", checkLastSignTime+"");
        minsMap.put("checkLastSignTimesource", checkLastSignTimesource+"");
        sourceMap.put(checkLastSignTimesource, minsMap);
      }
      hasSigned = true;
    }
    mutiMap.put("hasSign", hasSigned ? "1":"0");
    mutiMap.put("hasMins", ""+(hasMins <=0 ? 0 : hasMins));
    mutiMap.put("sourceMap", sourceMap);
    return mutiMap;
  }

  /**
   * 记录下多次签退生成加班数据问题
   * @param resourceId
   * @param taskDate
   * @param taskSignTime
   * @param lastWorkTime
   * @param workMins
   * @param signInTime4Out
   * @param mins
   * @param signDate
   * @param timesource
   * @param uuid
   * @param tiaoxiuId
   */
  private void logMultiSign(String resourceId, String taskDate, String taskSignTime,
      String lastWorkTime, int workMins, String signInTime4Out, long mins, String signDate,
      String timesource, String uuid, String tiaoxiuId) throws Exception{
    RecordSet rs = new RecordSet();
    String logSql = "insert into kq_overtime_signtask(resourceid,belongdate,signtime,lastworktime,workmins,signInTime4Out,mins,signDate,timesource,uuid,tiaoxiuId) values(?,?,?,?,?,?,?,?,?,?,?) ";
    boolean isLog = false;
    if("before".equalsIgnoreCase(timesource)){
        isLog = rs.executeUpdate(logSql,resourceId,taskDate,lastWorkTime,taskSignTime,workMins,signInTime4Out,mins,signDate,timesource,uuid,tiaoxiuId);
      }else{
        isLog = rs.executeUpdate(logSql,resourceId,taskDate,taskSignTime,lastWorkTime,workMins,signInTime4Out,mins,signDate,timesource,uuid,tiaoxiuId);
      }

    kqLog.info("记录下多次签退生成加班数据问题 logMultiSign:logSql:"+logSql+":resourceId:"+resourceId+":isLog:"+isLog
        +":taskDate:"+taskDate+":taskSignTime:"+taskSignTime+":lastWorkTime:"+lastWorkTime+":workMins:"+workMins+":signInTime4Out:"
        +signInTime4Out+":mins:"+mins+":signDate:"+signDate+":timesource:"+timesource);

  }

  /**
   * 无需审批，根据打开时间计算加班时长
   * 节假日处理
   * @param taskBean
   * @param fullFormatter
   */
  private void doMode3ChangeType1(KQTaskBean taskBean,
      DateTimeFormatter fullFormatter) throws Exception{
    String taskDate = taskBean.getTaskDate();
    String resourceId = taskBean.getResourceId();
    String taskSignTime = taskBean.getTaskSignTime();
    String signInTime4Out = taskBean.getSignInTime4Out();
    String signDate = taskBean.getSignDate();
    String signEndDate= taskBean.getSignEndDate();
    String timesource = Util.null2String(taskBean.getTimesource());
    String up_tiaoxiuid = taskBean.getTiaoxiuId();
    boolean isBefore = false;
    if("before".equalsIgnoreCase(timesource)){
      isBefore = true;
    }
    if(taskSignTime.length() > 0 && signInTime4Out.length() > 0){

      long mins = calNonWorkDuration(signInTime4Out,taskSignTime,taskDate,resourceId);
      if(isBefore && signEndDate.compareTo(signDate) < 0){
        //打卡日期和归属日期不是同一天的话
        String fromDateTime = signEndDate+" "+signInTime4Out;
        String toDateTime = signDate+" "+taskSignTime;
//        还需要交换一下开始日期和结束日期
        String tmpDate = signDate;
        signDate = signEndDate;
        signEndDate = tmpDate;
        mins = Duration.between(LocalDateTime.parse(fromDateTime, fullFormatter), LocalDateTime.parse(toDateTime, fullFormatter)).toMinutes();
      }
      if(mins > 0){
        int minimumUnit = KQOvertimeRulesBiz.getMinimumLen(resourceId, taskDate);
        int unit = KQOvertimeRulesBiz.getMinimumUnit();
        int paidLeaveEnable = KQOvertimeRulesBiz.getPaidLeaveEnable(resourceId, taskDate);
        paidLeaveEnable = paidLeaveEnable == 1?1:0;
        if(mins >= minimumUnit){
          if(taskSignTime.length() == 5){
            taskSignTime += ":00";
          }
          RecordSet rs = new RecordSet();
          String tmp_taskSignTime = signEndDate+" "+taskSignTime;
          Map<String,Object> mutiMap = checkMultiSign(resourceId,taskDate,tmp_taskSignTime,fullFormatter,timesource);
          String hasSign  = Util.null2String(mutiMap.get("hasSign"));
          Map<String,Map<String,String>> sourceMap = (Map<String, Map<String, String>>) mutiMap.get("sourceMap");
          String before_checkLastSignTime  = "";
          String before_checkLastSignDate  = "";
          double before_hasMins = 0.0;

          String after_checkLastSignTime  = "";
          String after_checkLastSignDate  = "";
          double after_hasMins = 0.0;
          if(sourceMap.containsKey("before")){
            Map<String,String> tmpMap = sourceMap.get("before");
            before_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            before_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            before_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          if(sourceMap.containsKey("after")){
            Map<String,String> tmpMap = sourceMap.get("after");
            after_checkLastSignDate = Util.null2String(tmpMap.get("checkLastSignDate"));
            after_checkLastSignTime = Util.null2String(tmpMap.get("checkLastSignTime"));
            after_hasMins = Util.getDoubleValue(Util.null2String(tmpMap.get("hasMins")),0.0);
          }
          long hasMins  = Long.parseLong(Util.null2String(mutiMap.get("hasMins")));

          String fromtime = "";
          String totime = "";
          if("1".equalsIgnoreCase(hasSign)){
            String checkLastSignTime = "";
            if(isBefore){
              checkLastSignTime = before_checkLastSignTime;
            }else{
              checkLastSignTime = after_checkLastSignTime;
            }
            if(checkLastSignTime.compareTo(signInTime4Out) > 0){
              fromtime = checkLastSignTime;
            }else{
              fromtime = signInTime4Out;
            }
            totime = taskSignTime;
            if(isBefore){
              mins  = (long) (mins-before_hasMins);
            }else{
              mins  = (long) (mins-after_hasMins);
            }
          }else{
            fromtime = signInTime4Out;
            totime = taskSignTime;
          }
          mins = mins < 0 ? 0 : mins;

          String tiaoxiuId = "";
          if(up_tiaoxiuid.length() > 0 && Util.getIntValue(up_tiaoxiuid) > 0){
            boolean is_tiaoxiuId = KQBalanceOfLeaveBiz.updateExtraAmountByDis5(up_tiaoxiuid,Util.getDoubleValue(mins+""), "");
            tiaoxiuId = up_tiaoxiuid;
          }else{
            tiaoxiuId = KQBalanceOfLeaveBiz.addExtraAmountByDis5(resourceId,taskDate,mins+"","0","","","",taskDate,null);
          }

          if(Util.getIntValue(tiaoxiuId) > 0){
            kqLog.info("生成加班数据成功  doMode3ChangeType1:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }else{
            kqLog.info("生成加班数据失败 doMode3ChangeType1:resourceId:"+resourceId+":taskDate:"+taskDate+":mins:"+mins);
          }
          String uuid = UUID.randomUUID().toString();
          String flow_overtime_sql = "insert into kq_flow_overtime (requestid,resourceid,fromdate,fromtime,todate,totime,duration_min,expiringdate,belongdate,workMins,durationrule,changetype,paidLeaveEnable,computingMode,tiaoxiuId,uuid)"+
              " values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ";
          boolean isUp = rs.executeUpdate(flow_overtime_sql, "",resourceId,signDate,fromtime,signEndDate,totime,mins,"",taskDate,"",unit,"1",paidLeaveEnable,"3",tiaoxiuId,uuid);
          if(!isUp){
            kqLog.info("doMode2ChangeType1 加班数据记录失败！！！");
          }

          logMultiSign(resourceId,taskDate,taskSignTime,"",0,signInTime4Out,mins,signEndDate,timesource,uuid,tiaoxiuId);
        }else{
          kqLog.info("签到签退 生成加班数据为 doMode3ChangeType1:实际加班时长mins:"+mins+":最小加班时长minimumUnit:"+minimumUnit);
        }

      }else{
        kqLog.info("签到签退 生成加班数据为 doMode3ChangeType1:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
      }
    }else{
      kqLog.info("签到签退 生成加班数据异常 doMode3ChangeType1:taskSignTime:"+taskSignTime+":signInTime4Out:"+signInTime4Out);
    }

  }

  /**
   * 根据每一天的班次来拆分
   * @param splitBean
   * @param splitBeans
   * @return
   */
  public void doSerialSplitList(SplitBean splitBean,List<SplitBean> splitBeans) throws Exception{
    String resourceid = splitBean.getResourceId();
    String fromDate = splitBean.getFromdatedb();
    String toDate = splitBean.getTodatedb();
    DurationTypeEnum durationTypeEnum = splitBean.getDurationTypeEnum();

    LocalDate localFromDate = LocalDate.parse(fromDate);
    LocalDate localToDate = LocalDate.parse(toDate);
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    LocalDate preFromDate = localFromDate.minusDays(1);

    KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();

    boolean shouldLog = true;
    if(durationTypeEnum == DurationTypeEnum.COMMON_CAL){
      shouldLog = false;
    }
    if(shouldLog){
      kqLog.info("resourceid::"+resourceid+"::doSerialSplitList:"+ (splitBean != null ? JSON.toJSONString(splitBean):null));
    }
    boolean is_flow_humanized = KQSettingsBiz.is_flow_humanized();
    long betweenDays = localToDate.toEpochDay() - preFromDate.toEpochDay();
    for (int i = 0; i <= betweenDays; i++) {
      LocalDate curLocalDate = preFromDate.plusDays(i);
      String splitDate = curLocalDate.format(dateFormatter);
      ShiftInfoBean shiftInfoBean = null;
      if(shouldLog){
        shiftInfoBean = KQDurationCalculatorUtil.getWorkTime(resourceid, splitDate,false);
      }else{
        shiftInfoBean = KQDurationCalculatorUtil.getWorkTime(resourceid, splitDate,false,false);
      }
      if(shouldLog){
        kqLog.info("resourceid::"+resourceid+"::splitDate:"+splitDate+":shiftInfoBean:"+ (shiftInfoBean != null ? JSON.toJSONString(shiftInfoBean):null));
      }

      int[] initArrays = kqTimesArrayComInfo.getInitArr();
      boolean isSplit = true;
      if(shiftInfoBean != null){
        splitBean.setSerialid(Util.null2String(shiftInfoBean.getSerialid()));
        if(!shiftInfoBean.isIsfree()){
          isSplit = commonShiftSplit(shiftInfoBean,splitBean,i,kqTimesArrayComInfo,initArrays,localFromDate,localToDate,betweenDays,shouldLog,is_flow_humanized);
          if(!isSplit){
            continue;
          }
        }else{
          isSplit = freeShiftSplit(shiftInfoBean,splitBean,i,kqTimesArrayComInfo,initArrays,localFromDate,localToDate,betweenDays);
          if(!isSplit){
            continue;
          }
        }
        //根据单位转换时长
        turnDuration(splitBean,false);
      }else{
        //非工作时长
        String computingMode = splitBean.getComputingMode();
        if("2".equalsIgnoreCase(computingMode)){
          String filterholidays = splitBean.getFilterholidays();
          boolean is_filterholidays = check_filterholidays(filterholidays,resourceid,splitDate,i);
          if(is_filterholidays){
            continue;
          }
          ShiftInfoBean preShiftInfoBean = KQDurationCalculatorUtil.getWorkTime(resourceid, curLocalDate.minusDays(1).format(dateFormatter),false);
          isSplit = nonWorkDaySplit(preShiftInfoBean,splitBean,i,kqTimesArrayComInfo,initArrays,localFromDate,localToDate,betweenDays);
          if(!isSplit){
            continue;
          }
          //根据单位转换时长
          turnDuration(splitBean,true);
        }else{
          if(shouldLog){
            kqLog.info("resourceid::"+resourceid+"::doSerialSplitList splitDate:"+splitDate+":resourceid:"+resourceid+"是非工作日");
          }
          continue;
        }
      }

      SplitBean newsplitBean = new SplitBean();
      //然后把bean重新赋值下，根据拆分后的时间
      //BeanUtils.copyProperties(splitBean, newsplitBean);
      BeanUtil.copyProperties(splitBean,newsplitBean);
      newsplitBean.setFromDate(splitDate);
      newsplitBean.setToDate(splitDate);
      newsplitBean.setBelongDate(splitDate);
      splitBeans.add(newsplitBean);
    }
  }

  /**
   * 监测按照自然日计算，根据排除休息日和节假日的设置，判断当前自然日是否需要排除计算
   * @param filterholidays
   * @param resourceid
   * @param splitDate
   * @param i
   */
  public boolean check_filterholidays(String filterholidays, String resourceid, String splitDate,
      int i) {
    boolean is_filterholidays = false;
    if (i == 0) {
      return is_filterholidays;
    }
    if(Util.null2String(filterholidays).length() > 0){
      List<String> filterholidayList = Arrays.asList(filterholidays.split(","));
      if(filterholidayList != null && !filterholidayList.isEmpty()){
        //日期类型：1-节假日、2-工作日、3-休息日(节假日设置的优先级高于考勤组中的设置)
        int changeType = KQOvertimeRulesBiz.getChangeType(resourceid,splitDate);
        if(filterholidayList.contains("1")){
          //排除节假日
          if(filterholidayList.contains("2")){
            //排除休息日
            if(changeType == 3 || changeType == 1){
              is_filterholidays = true;
            }
          }else {
            if(changeType == 1){
              is_filterholidays = true;
            }
          }
        }else if(filterholidayList.contains("2")){
          //排除休息日
          if(changeType == 3){
            is_filterholidays = true;
          }
        }else{
          //不排除休息日，也不排除节假日
        }
      }
    }
    return is_filterholidays;
  }

  /**
   * 自由工时的流程数据拆分
   * @param shiftInfoBean
   * @param splitBean
   * @param i
   * @param kqTimesArrayComInfo
   * @param initArrays
   * @param localFromDate
   * @param localToDate
   * @param betweenDays
   * @return
   */
  public boolean freeShiftSplit(ShiftInfoBean shiftInfoBean, SplitBean splitBean, int i, KQTimesArrayComInfo kqTimesArrayComInfo, int[] initArrays, LocalDate localFromDate, LocalDate localToDate,
      long betweenDays) {

    String fromTime = splitBean.getFromtimedb();
    String toTime = splitBean.getTotimedb();
    int fromtimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(fromTime);
    int totimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(toTime);
    int flowbegintimeIndex = -1;
    int flowendtimeIndex = -1;

    boolean isSplit = true;
    String freeSignStart = shiftInfoBean.getFreeSignStart();
    String freeSignEnd = shiftInfoBean.getFreeSignEnd();
    if(freeSignStart.length() == 0){
      shiftInfoBean.setD_Mins(0.0);
      isSplit = false;
      return isSplit;
    }
    int freeSignStartIndex = kqTimesArrayComInfo.getArrayindexByTimes(freeSignStart);
    int freeSignEndIndex = kqTimesArrayComInfo.getArrayindexByTimes(freeSignEnd);
    int freeWorkMins = Util.getIntValue(shiftInfoBean.getFreeWorkMins());
    if (i == 0) {
      isSplit = false;
      return isSplit;
    }else{
      if(localFromDate.isEqual(localToDate)){
        if(fromtimeIndex > freeSignStartIndex){
          flowbegintimeIndex = fromtimeIndex;
          flowendtimeIndex = totimeIndex;
        }else{
          flowbegintimeIndex = freeSignStartIndex;
          flowendtimeIndex = totimeIndex;
        }
      }else{
        if (i == 1) {
          if(fromtimeIndex > freeSignStartIndex){
            flowbegintimeIndex = fromtimeIndex;
            flowendtimeIndex = 1440;
          }else{
            flowbegintimeIndex = freeSignStartIndex;
            flowendtimeIndex = 1440;
          }
        } else if (i == betweenDays) {
          if(totimeIndex > freeSignStartIndex){
            flowbegintimeIndex = freeSignStartIndex;
            flowendtimeIndex = totimeIndex;
          }else{
            isSplit = false;
            return isSplit;
          }
        } else {
          flowbegintimeIndex = freeSignStartIndex;
          flowendtimeIndex = freeSignEndIndex;
        }
      }
    }
    kqLog.info("resourceid::"+splitBean.getResourceId()+"::自由班制 isSplit之前 i::"+i+"::flowbegintimeIndex:"+flowbegintimeIndex+":flowendtimeIndex:"+ flowendtimeIndex);

    if(isSplit){
      String splitFromTime = kqTimesArrayComInfo.getTimesByArrayindex(flowbegintimeIndex);
      String splitToTime = kqTimesArrayComInfo.getTimesByArrayindex(flowendtimeIndex);
      int flowMins = flowendtimeIndex - flowbegintimeIndex;
      flowMins = flowMins > freeWorkMins ? freeWorkMins : flowMins;
      flowMins = flowMins > 0 ? flowMins : 0;
      if(flowMins < 0){
        kqLog.info("resourceid::"+splitBean.getResourceId()+"::自由班制 不记录中间表 i::"+i+"::flowbegintimeIndex:"+flowbegintimeIndex+":flowendtimeIndex:"+ flowendtimeIndex+":flowMins:"+ flowMins);
        return false;
      }
      splitBean.setD_Mins(flowMins);
      splitBean.setWorkmins(freeWorkMins);
      splitBean.setOneDayHour((freeWorkMins/60.0));
      splitBean.setFromTime(splitFromTime);
      splitBean.setToTime(splitToTime);
    }
    return isSplit;
  }

  /**
   * 非工作时长（自然日计算的）
   * @param preShiftInfoBean
   * @param splitBean
   * @param i
   * @param kqTimesArrayComInfo
   * @param initArrays
   * @param localFromDate
   * @param localToDate
   * @param betweenDays
   * @return
   */
  public boolean nonWorkDaySplit(ShiftInfoBean preShiftInfoBean, SplitBean splitBean, int i,
      KQTimesArrayComInfo kqTimesArrayComInfo, int[] initArrays, LocalDate localFromDate,
      LocalDate localToDate, long betweenDays) {

    boolean isSplit = true;
    if(preShiftInfoBean != null){
      String isAcross = preShiftInfoBean.getIsAcross();
      if("1".equalsIgnoreCase(isAcross)){
        List<int[]> workAcrossIndex = preShiftInfoBean.getWorkAcrossIndex();
        if(workAcrossIndex !=null && !workAcrossIndex.isEmpty()){
          int[] workIndexs = workAcrossIndex.get(workAcrossIndex.size()-1);
          return nonWorkDayCommonSplit(splitBean, i, kqTimesArrayComInfo, localFromDate, localToDate, betweenDays,workIndexs[1]);
        }
      }else{
        return nonWorkDayCommonSplit(splitBean, i, kqTimesArrayComInfo, localFromDate, localToDate, betweenDays,-1);
      }
    }else{
      return nonWorkDayCommonSplit(splitBean, i, kqTimesArrayComInfo, localFromDate, localToDate, betweenDays,-1);
    }

    return isSplit;
  }

  /**
   * 非工作时长（自然日计算的）
   * @param splitBean
   * @param i
   * @param kqTimesArrayComInfo
   * @param localFromDate
   * @param localToDate
   * @param betweenDays
   * @param preLastWorkIndex 如果前一天跨天，前一天最晚的下班时间
   * @return
   */
  public boolean nonWorkDayCommonSplit(SplitBean splitBean, int i,
      KQTimesArrayComInfo kqTimesArrayComInfo, LocalDate localFromDate,
      LocalDate localToDate, long betweenDays,int preLastWorkIndex) {
    boolean isSplit = true;

    String fromTime = splitBean.getFromtimedb();
    String toTime = splitBean.getTotimedb();
    int fromtimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(fromTime);
    int totimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(toTime);
    int flowbegintimeIndex = -1;
    int flowendtimeIndex = -1;

    if (i == 0) {
      isSplit = false;
      return isSplit;
    }else{
      if(localFromDate.isEqual(localToDate)){
        flowbegintimeIndex = fromtimeIndex;
        flowendtimeIndex = totimeIndex;
      }else{
        if(i == 1){
          flowbegintimeIndex = fromtimeIndex;
          flowendtimeIndex = 1440;
        }else if(i == betweenDays){
          flowbegintimeIndex = 0;
          flowendtimeIndex = totimeIndex;
        }else{
          flowbegintimeIndex = 0;
          flowendtimeIndex = 1440;
        }
      }
    }
    if(isSplit){
      String splitFromTime = kqTimesArrayComInfo.getTimesByArrayindex(flowbegintimeIndex);
      if(preLastWorkIndex > 0){
        if(preLastWorkIndex > flowbegintimeIndex){
          flowbegintimeIndex = preLastWorkIndex;
        }
      }
      if("2".equalsIgnoreCase(splitBean.getDurationrule())) {
        if ("2".equalsIgnoreCase(splitBean.getTimeselection())) {
          return WorkHalfUnitSplitChain.getSplitDurationBean4NonTime(kqTimesArrayComInfo,splitBean,flowbegintimeIndex,flowendtimeIndex);
        }
      }else{
        String splitToTime = kqTimesArrayComInfo.getTimesByArrayindex(flowendtimeIndex);
        int flowMins = flowendtimeIndex - flowbegintimeIndex;
        flowMins = flowMins > 0 ? flowMins : 0;
        splitBean.setD_Mins(flowMins);
        splitBean.setFromTime(splitFromTime);
        splitBean.setToTime(splitToTime);
      }
    }
    return isSplit;
  }

  /**
   * 固定班制和排班制的数据拆分
   * @param shiftInfoBean
   * @param splitBean
   * @param i
   * @param kqTimesArrayComInfo
   * @param initArrays
   * @param localFromDate
   * @param localToDate
   * @param betweenDays
   * @param shouldLog
   * @return
   */

  public boolean commonShiftSplit(ShiftInfoBean shiftInfoBean, SplitBean splitBean, int i,
                                  KQTimesArrayComInfo kqTimesArrayComInfo, int[] initArrays, LocalDate localFromDate,
                                  LocalDate localToDate, long betweenDays, boolean shouldLog) {
    return commonShiftSplit(shiftInfoBean,splitBean,i,kqTimesArrayComInfo,initArrays,localFromDate,localToDate,betweenDays,shouldLog,false);
  }
  public boolean commonShiftSplit(ShiftInfoBean shiftInfoBean, SplitBean splitBean, int i,
      KQTimesArrayComInfo kqTimesArrayComInfo, int[] initArrays, LocalDate localFromDate,
      LocalDate localToDate, long betweenDays, boolean shouldLog,boolean is_flow_humanized) {

    boolean isSplit = true;

    String fromTime = splitBean.getFromtimedb();
    String toTime = splitBean.getTotimedb();
    int fromtimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(fromTime);
    int totimeIndex = kqTimesArrayComInfo.getArrayindexByTimes(toTime);
    int flowbegintimeIndex = -1;
    int flowendtimeIndex = -1;

    String isAcross = shiftInfoBean.getIsAcross();
    List<String> real_allLongWorkTime = Lists.newArrayList();
    List<String> allLongWorkTime = shiftInfoBean.getAllLongWorkTime();
    CollectionUtils.addAll(real_allLongWorkTime,new Object[allLongWorkTime.size()]);
    Collections.copy(real_allLongWorkTime, allLongWorkTime);

    List<int[]> real_workLongTimeIndex = Lists.newArrayList();
    List<int[]> workLongTimeIndex = shiftInfoBean.getWorkLongTimeIndex();

    //list带数组，这里要深拷贝
    for(int[] tmp : workLongTimeIndex){
      int[] real_tmp = new int[tmp.length];
      System.arraycopy(tmp, 0, real_tmp, 0, tmp.length);
      real_workLongTimeIndex.add(real_tmp);
    }
    List<int[]> restLongTimeIndex = shiftInfoBean.getRestLongTimeIndex();
    if(real_allLongWorkTime == null || real_allLongWorkTime.isEmpty()){
      isSplit = false;
      return isSplit;
    }

    if(real_workLongTimeIndex.size() == 1){
      KQShiftRuleInfoBiz kqShiftRuleInfoBiz = new KQShiftRuleInfoBiz();
      kqShiftRuleInfoBiz.rest_workLongTimeIndex(shiftInfoBean,splitBean,real_workLongTimeIndex,kqTimesArrayComInfo,real_allLongWorkTime,is_flow_humanized);
    }

    //如果不跨天
    if(!isAcross.equalsIgnoreCase("1")){
      //如果是请假开始日期的前一天
      if (i == 0) {
        isSplit = false;
        return isSplit;
      }else{
        int firstTime = kqTimesArrayComInfo.getArrayindexByTimes(real_allLongWorkTime.get(0));
        int lastTime = kqTimesArrayComInfo.getArrayindexByTimes(real_allLongWorkTime.get(real_allLongWorkTime.size()-1));

        if(localFromDate.isEqual(localToDate)){
          flowbegintimeIndex = fromtimeIndex;
          flowendtimeIndex = totimeIndex;
        }else{
          if (i == 1) {
            flowbegintimeIndex = fromtimeIndex;
            flowendtimeIndex = lastTime;
          } else if (i == betweenDays) {
            flowbegintimeIndex = firstTime;
            flowendtimeIndex = totimeIndex;
          } else {
            flowbegintimeIndex = firstTime;
            flowendtimeIndex = lastTime;
          }
        }
        //工作时段里填充上1
        for(int j=0 ;real_workLongTimeIndex != null && j <real_workLongTimeIndex.size() ; j++){
          int[] longtimeIndexs = real_workLongTimeIndex.get(j);
          Arrays.fill(initArrays, longtimeIndexs[0],longtimeIndexs[1],1);
        }
        for(int j=0 ;restLongTimeIndex != null && j <restLongTimeIndex.size() ; j++){
          int[] resttimeIndexs = restLongTimeIndex.get(j);
          Arrays.fill(initArrays, resttimeIndexs[0],resttimeIndexs[1],-1);
        }
      }


    }else{
      int firstTime = kqTimesArrayComInfo.getArrayindexByTimes(real_allLongWorkTime.get(0));
      //lasttime一定是跨天的
      int lastTime = kqTimesArrayComInfo.getArrayindexByTimes(real_allLongWorkTime.get(real_allLongWorkTime.size()-1));
      if (i == 0) {
        int fromtimeIndex48 = kqTimesArrayComInfo.turn24to48TimeIndex(fromtimeIndex);
        int totimeIndex48 = kqTimesArrayComInfo.turn24to48TimeIndex(totimeIndex);
        if(fromtimeIndex48 < lastTime){
          flowbegintimeIndex = fromtimeIndex48;
          if(localFromDate.isEqual(localToDate)){
            if(totimeIndex48 < lastTime){
              flowendtimeIndex = totimeIndex48;
            }else{
              flowendtimeIndex = lastTime;
            }
          }else{
            if(betweenDays == 2){
              //流程上开始日期和结束日期就相差一天
              if((totimeIndex48+1440) < lastTime){
                flowendtimeIndex = totimeIndex48;
              }else{
                flowendtimeIndex = lastTime;
              }
            }else {
              //流程上开始日期和结束日期相差超过1天
              flowendtimeIndex = lastTime;
            }

          }
        }else{
          isSplit = false;
          return isSplit;
        }
      }else{
        if(localFromDate.isEqual(localToDate)){
          flowbegintimeIndex = fromtimeIndex;
          flowendtimeIndex = totimeIndex;
        }else{
          if (i == 1) {
            flowbegintimeIndex = fromtimeIndex;
            if((i+1) == (betweenDays)){
              int totimeIndex48 = kqTimesArrayComInfo.turn24to48TimeIndex(totimeIndex);
              if(totimeIndex48 < lastTime){
                flowendtimeIndex = totimeIndex48;
              }else{
                flowendtimeIndex = lastTime;
              }
            }else{
              flowendtimeIndex = lastTime;
            }
          } else if (i == (betweenDays-1)) {
            flowbegintimeIndex = firstTime;
            int totimeIndex48 = kqTimesArrayComInfo.turn24to48TimeIndex(totimeIndex);
            if(totimeIndex48 < lastTime){
              flowendtimeIndex = totimeIndex48;
            }else{
              flowendtimeIndex = lastTime;
            }
          } else if (i == betweenDays) {
            flowbegintimeIndex = firstTime;
            flowendtimeIndex = totimeIndex;
            if(firstTime > totimeIndex){
              isSplit = false;
              return isSplit;
            }
          } else {
            flowbegintimeIndex = firstTime;
            flowendtimeIndex = lastTime;
            if(firstTime > lastTime){
              isSplit = false;
              return isSplit;
            }
          }
        }
      }
      //工作时段里填充上1
      for(int j=0 ;j <real_workLongTimeIndex.size() ; j++){
        int[] longtimeIndexs = real_workLongTimeIndex.get(j);
        Arrays.fill(initArrays, longtimeIndexs[0],longtimeIndexs[1],1);

      }
      for(int j=0 ;j <restLongTimeIndex.size() ; j++){
        int[] resttimeIndexs = restLongTimeIndex.get(j);
        Arrays.fill(initArrays, resttimeIndexs[0],resttimeIndexs[1],-1);
      }
    }
    if(isSplit){

      if("2".equalsIgnoreCase(splitBean.getDurationrule())) {
        if ("2".equalsIgnoreCase(splitBean.getTimeselection())) {
          return WorkHalfUnitSplitChain.getSplitDurationBean4Time(initArrays,shiftInfoBean,kqTimesArrayComInfo,splitBean,flowbegintimeIndex,flowendtimeIndex);
        }
      }else{
        String splitFromTime = kqTimesArrayComInfo.getTimesByArrayindex(flowbegintimeIndex);
        String splitToTime = kqTimesArrayComInfo.getTimesByArrayindex(flowendtimeIndex);
        int flowMins = kqTimesArrayComInfo.getCnt(initArrays, flowbegintimeIndex, flowendtimeIndex, 1);
        if(shouldLog){
          kqLog.info("resourceid:"+splitBean.getResourceId()+":::i::"+i+"::flowbegintimeIndex:"+flowbegintimeIndex+":flowendtimeIndex:"+ flowendtimeIndex+":flowMins:"+ flowMins);
        }
        flowMins = flowMins > 0 ? flowMins : 0;
        if(flowMins < 0){
          if(shouldLog){
            kqLog.info("resourceid:"+splitBean.getResourceId()+":::不记录中间表 i::"+i+"::flowbegintimeIndex:"+flowbegintimeIndex+":flowendtimeIndex:"+ flowendtimeIndex+":flowMins:"+ flowMins);
          }
          return false;
        }
        splitBean.setD_Mins(flowMins);
        splitBean.setWorkmins(shiftInfoBean.getWorkmins());
        splitBean.setFromTime(splitFromTime);
        splitBean.setToTime(splitToTime);
      }

    }
    return isSplit;
  }

  /**
   * 根据单位来转换时长
   * @param splitBean
   * @param isComputingMode2 是否按照自然日计算
   */
  private void turnDuration(SplitBean splitBean,boolean isComputingMode2) {
    String durationrule = splitBean.getDurationrule();

    //计算规则 1-按天请假 2-按半天请假 3-按小时请假 4-按整天请假 5半小时 6整小时
    //按照天和小时的可以在这里计算，半天的和整天的单独处理
    if("1".equalsIgnoreCase(durationrule)){
      durationrule1(splitBean,isComputingMode2);
    }else if("3".equalsIgnoreCase(durationrule)){
      durationrule3(splitBean,isComputingMode2);
    }else if("5".equalsIgnoreCase(durationrule)){
      String conversion= splitBean.getConversion();
      if(conversion.length() > 0 && Util.getIntValue(conversion) > 0){
        double conversionMins = 0.0;
        int halfHourInt = 30;
        KQOverTimeRuleCalBiz kqOverTimeRuleCalBiz = new KQOverTimeRuleCalBiz();
        conversionMins = kqOverTimeRuleCalBiz.getConversionMins(halfHourInt,splitBean.getD_Mins(),Util.getIntValue(conversion));
        splitBean.setD_Mins(conversionMins);
      }
      durationrule3(splitBean,isComputingMode2);
    }else if("6".equalsIgnoreCase(durationrule)){
      String conversion= splitBean.getConversion();
      if(conversion.length() > 0 && Util.getIntValue(conversion) > 0){
        double conversionMins = 0.0;
        int wholeHourInt = 60;
        KQOverTimeRuleCalBiz kqOverTimeRuleCalBiz = new KQOverTimeRuleCalBiz();
        conversionMins = kqOverTimeRuleCalBiz.getConversionMins(wholeHourInt,splitBean.getD_Mins(),Util.getIntValue(conversion));
        splitBean.setD_Mins(conversionMins);
      }
      durationrule3(splitBean,isComputingMode2);
    }

  }

  /**
   * 按天请假
   * @param splitBean
   * @param isComputingMode2 是否按照自然日计算
   * @return
   */
  private void durationrule1(SplitBean splitBean,boolean isComputingMode2) {
    String computingMode = splitBean.getComputingMode();
    double d_Mins = splitBean.getD_Mins();
    double curDays = 0.0;
    if(isComputingMode2){
      double oneDayHour = splitBean.getOneDayHour();
      if(oneDayHour > 0){
        double oneDayMins = oneDayHour * 60.0;
        splitBean.setWorkmins(((int)oneDayMins));
        if(d_Mins > oneDayMins){
          d_Mins = oneDayMins;
          splitBean.setD_Mins(d_Mins);
        }
        curDays = (d_Mins)/(oneDayMins);
        curDays = curDays > 0 ? curDays : 0.0;
      }
    }else{
      int workmins = splitBean.getWorkmins();
      if(workmins > 0){
        curDays = d_Mins/(workmins*1.0);
        curDays = curDays > 0 ? curDays : 0.0;
      }
    }

    splitBean.setDuration(KQDurationCalculatorUtil.getDurationRound5(""+(curDays)));
  }

  /**
   * 按小时请假
   * @param splitBean
   * @param isComputingMode2
   * @return
   */
  private double durationrule3(SplitBean splitBean, boolean isComputingMode2) {
    double D_Mins = splitBean.getD_Mins();
    double hours = 0.0;
    if(isComputingMode2){
      double oneDayHour = splitBean.getOneDayHour();
      if(oneDayHour > 0) {
        double oneDayMins = oneDayHour * 60.0;
        splitBean.setWorkmins(((int)oneDayMins));
        if(D_Mins > oneDayMins){
          D_Mins = oneDayMins;
          splitBean.setD_Mins(D_Mins);
        }
      }
    }
    hours = D_Mins/60.0;
    hours = hours > 0 ? hours : 0.0;
    splitBean.setDuration(KQDurationCalculatorUtil.getDurationRound(""+(hours)));
    return hours;
  }


  /**
   * 根据传入的日期和时间拆分成每一天一条的集合
   * @param fromDate
   * @param toDate
   * @param fromTime
   * @param toTime
   * @return
   */
  public static List<Map<String,String>> getSplitList(String fromDate,String toDate,String fromTime,String toTime){
    List<Map<String,String>> splitLists = new ArrayList<>();
    Map<String,String> splitMap = new HashMap<>();

    LocalDate localFromDate = LocalDate.parse(fromDate);
    LocalDate localToDate = LocalDate.parse(toDate);
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    //进入到这里表示是多天
    if (localFromDate.isBefore(localToDate)) {
      long betweenDays = localToDate.toEpochDay() - localFromDate.toEpochDay();
      for (int i = 0; i <= betweenDays; i++) {
        splitMap = new HashMap<>();
        LocalDate curLocalDate = localFromDate.plusDays(i);

        splitMap.put("splitDate", curLocalDate.format(dateFormatter));
        if (i == 0) {
          splitMap.put("fromTime", fromTime);
          splitMap.put("toTime", "23:59");
        } else if (i == betweenDays) {
          splitMap.put("fromTime", "00:00");
          splitMap.put("toTime", toTime);
        } else {
          splitMap.put("fromTime", "00:00");
          splitMap.put("toTime", "23:59");
        }
        splitLists.add(splitMap);
      }
    } else if (localFromDate.isEqual(localToDate)) {
      //同一天
      splitMap.put("splitDate", localFromDate.format(dateFormatter));
      splitMap.put("fromTime", fromTime);
      splitMap.put("toTime", toTime);
      splitLists.add(splitMap);
    }
    return splitLists;
  }

  /**
   * 根据传入的日期和时间拆分成半天规则的集合
   * @param fromDate
   * @param toDate
   * @param fromTime
   * @param toTime
   * @return
   */
  public static List<Map<String,String>> getSplitHalfDayList(String fromDate,String toDate,String fromTime,String toTime){
    List<Map<String,String>> splitLists = new ArrayList<>();
    Map<String,String> splitMap = new HashMap<>();

    LocalDate localFromDate = LocalDate.parse(fromDate);
    LocalDate localToDate = LocalDate.parse(toDate);
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    //进入到这里表示是多天
    if (localFromDate.isBefore(localToDate)) {
      long betweenDays = localToDate.toEpochDay() - localFromDate.toEpochDay();
      for (int i = 0; i <= betweenDays; i++) {
        splitMap = new HashMap<>();
        LocalDate curLocalDate = localFromDate.plusDays(i);
        splitMap.put("splitDate", curLocalDate.format(dateFormatter));
        if (i == 0) {
          int foreOrAfter = SplitSelectSet.foreOrAfter(fromTime, SplitSelectSet.afternoon_end);
          splitMap.put("foreOrAfter", ""+foreOrAfter);
        } else if (i == betweenDays) {
          int foreOrAfter = SplitSelectSet.foreOrAfter(SplitSelectSet.forenoon_start, toTime);
          splitMap.put("foreOrAfter", ""+foreOrAfter);
        } else {
          splitMap.put("foreOrAfter", ""+ SplitSelectSet.fore_after_index);
        }
        splitLists.add(splitMap);
      }
    } else if (localFromDate.isEqual(localToDate)) {
      //同一天
      splitMap.put("splitDate", localFromDate.format(dateFormatter));
      int foreOrAfter = SplitSelectSet.foreOrAfter(fromTime, toTime);
      splitMap.put("foreOrAfter", ""+foreOrAfter);
      splitLists.add(splitMap);
    }
    return splitLists;
  }

  /**
   * 根据传入的日期分成集合
   * @param fromDate
   * @param toDate
   * @return
   */
  public static List<Map<String,String>> getSplitDayList(String fromDate,String toDate){
    List<Map<String,String>> splitLists = new ArrayList<>();
    Map<String,String> splitMap = new HashMap<>();

    LocalDate localFromDate = LocalDate.parse(fromDate);
    LocalDate localToDate = LocalDate.parse(toDate);
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    long betweenDays = localToDate.toEpochDay() - localFromDate.toEpochDay();
    for (int i = 0; i <= betweenDays; i++) {
      splitMap = new HashMap<>();
      LocalDate curLocalDate = localFromDate.plusDays(i);
      splitMap.put("splitDate", curLocalDate.format(dateFormatter));
      splitLists.add(splitMap);
    }
    return splitLists;
  }

  /**
   * 对于工作日加班判断是否是开启了晚走晚到的规则，开启了的话，不试做加班
   * @return
   */
  public boolean checkIsLateoutlatein(String resourceId ,String taskDate){
    boolean isLateoutlatein = false;
    KQWorkTime kqWorkTime = new KQWorkTime();
    Map<String,Object> serialInfo = kqWorkTime.getSerialInfo(resourceId, taskDate, false);
    if(serialInfo!=null&&serialInfo.size()>0){
      String serialid = Util.null2String(serialInfo.get(taskDate));
      if(serialid.length() > 0){
        KQShiftPersonalizedRuleCominfo ruleCominfo = new KQShiftPersonalizedRuleCominfo();
        KQShiftPersonalizedRuleDetailComInfo ruleDetailComInfo = new KQShiftPersonalizedRuleDetailComInfo();
        String personalizedruleid = ruleCominfo.getID(serialid);
        Map<String,Object> ruleDetailMap = ruleDetailComInfo.getPersonalizedRuleDetail(personalizedruleid);
        if(ruleDetailMap != null && !ruleDetailMap.isEmpty()){
          List<Object> workSectionList = (List<Object>) ruleDetailMap.get("lateoutlatein");
          if(workSectionList != null && !workSectionList.isEmpty()){
//                  workSectionList里存的enable都是一致的，取一个就行
            Map<String,Object> sectionMap = (Map<String, Object>) workSectionList.get(0);
            if(sectionMap != null && !sectionMap.isEmpty()){
              String enable = Util.null2String(sectionMap.get("enable"));
              if("1".equalsIgnoreCase(enable)){
                isLateoutlatein = true;
                kqLog.info("resourceid:"+resourceId+":taskDate:"+taskDate+":::开启了晚走晚到规则，不计算加班 checkIsLateoutlatein:resourceId:"+resourceId+":taskDate:"+taskDate+":serialid:"+serialid);
                return isLateoutlatein;
              }
            }
          }
        }
      }
    }

    return isLateoutlatein;
  }

  /**
   * 推送 补打卡，外勤，考勤同步数据，触发加班规则生成加班数据处理
   * @param fromDate
   * @param toDate
   * @param resourceids 支持多个人的
   */
  public static void pushOverTimeTasksAll(String fromDate,String toDate,String resourceids){

    kqLog.info("pushOverTimeTasksAll 参数为:resourceids:"+resourceids+":fromDate:"+fromDate+":toDate:"+toDate);
    if(resourceids.length() == 0 || fromDate.length() == 0 || toDate.length() == 0){
      return ;
    }
    List<KQTaskBean> tasks = new ArrayList<>();
    String[] resourceid_arr = resourceids.split(",");
    for(int i = 0 ; i < resourceid_arr.length ; i++){
      pushOverTimeTasks(fromDate, toDate, resourceid_arr[i],tasks);
    }
    if(!tasks.isEmpty()){
      KQQueue.writeTasks(tasks);
    }

  }

  /**
   * 推送 补打卡，外勤，考勤同步数据，触发加班规则生成加班数据处理
   * @param fromDate
   * @param toDate
   * @param resourceid
   * @param tasks
   */
  public static void pushOverTimeTasks(String fromDate,String toDate,String resourceid,List<KQTaskBean> tasks){

    try {
      if(true){
        KQTaskBean kqTaskBean = new KQTaskBean();
        kqTaskBean.setResourceId(resourceid);
        kqTaskBean.setOvertime_fromdate(fromDate);
        kqTaskBean.setOvertime_todate(toDate);
        tasks.add(kqTaskBean);
      }else{
        pushOverTimeTasks_old(fromDate,toDate,resourceid,tasks);
      }
    } catch (Exception e) {
      baseBean.writeLog(e);
    }
  }

  public static void pushOverTimeTasks_old(String fromDate, String toDate, String resourceid, List<KQTaskBean> tasks) {

//      kqLog.info("批量加班推送 pushOverTimeTasks:fromDate:"+fromDate+"::toDate:"+toDate+"::resourceid:"+resourceid);

    LocalDate localFromDate = LocalDate.parse(fromDate);
    LocalDate localToDate = LocalDate.parse(toDate);
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    long betweenDays = localToDate.toEpochDay() - localFromDate.toEpochDay();
    for (int i = 0; i <= betweenDays; i++) {
      LocalDate curLocalDate = localFromDate.plusDays(i);
      LocalDate preCurLocalDate = curLocalDate.minusDays(1);
      LocalDate nextCurLocalDate = curLocalDate.plusDays(1);
      String splitDate = curLocalDate.format(dateFormatter);
      String pre_splitDate = preCurLocalDate.format(dateFormatter);
      String next_splitDate = nextCurLocalDate.format(dateFormatter);
      Map<String, Object> pre_todayLineMap = KQDurationCalculatorUtil.getWorkButton(resourceid, pre_splitDate,false);
      Map<String, Object> todayLineMap = KQDurationCalculatorUtil.getWorkButton(resourceid, splitDate,false);
      //因为现在是上班前都归属前一天，所以这里存一下上班前的开始时间
      Map<String,String> workTimeStartMap = new HashMap<>();
      //如果昨天是工作日，昨天的上班时间作为今天的允许加班开始时间（因为下班后加班都属于前一天的）
      String pre_workTime = "";
      if(pre_todayLineMap.get("signTime") != null){
        pre_workTime = getEndWorktime(pre_todayLineMap,splitDate,workTimeStartMap,resourceid,pre_splitDate);
      }else{
        workTimeStartMap.put("pre_isrest", "1");
      }
      //如果今天是非工作日，明天是工作日，明天的上班时间作为今天的允许加班结束时间
      String next_workTime = "";
      Map<String, Object> next_todayLineMap = KQDurationCalculatorUtil.getWorkButton(resourceid, next_splitDate,false);

      if(next_todayLineMap.get("signTime") != null){
        next_workTime = getStartWorktime(next_todayLineMap,splitDate,workTimeStartMap);
      }else{
        workTimeStartMap.put("next_isrest", "1");
      }
      boolean isTodayWorkDay = todayLineMap.get("signTime") != null;

      kqLog.info("pushOverTimeTasks splitDate:"+splitDate+":isTodayWorkDay:"+isTodayWorkDay+":pre_workTime:"+pre_workTime+":next_workTime:"+next_workTime);
      //如果当前日期是工作日
      if(isTodayWorkDay){
        List<Map<String, String>> todaySignTime = (List<Map<String, String>>)todayLineMap.get("signTime");
        if(todaySignTime != null && !todaySignTime.isEmpty()){
          overTime4Work(splitDate, pre_splitDate, resourceid, tasks, todayLineMap, todaySignTime,workTimeStartMap);
        }else{
          overTime4NonWork(splitDate, pre_workTime, resourceid, tasks,next_workTime,workTimeStartMap,pre_splitDate);
        }
      }else{
        overTime4NonWork(splitDate, pre_workTime, resourceid, tasks,next_workTime,workTimeStartMap,pre_splitDate);
      }
    }
//      kqLog.info("批量加班推送 数据为:"+(JSON.toJSONString(tasks)));

  }

  /**
   * 获取最晚允许下班时间
   * @param pre_todayLineMap
   * @param splitDate
   * @param workTimeStartMap
   * @param resourceid
   * @param pre_splitDate
   * @return
   */
  private static String getEndWorktime(Map<String, Object> pre_todayLineMap, String splitDate,
      Map<String, String> workTimeStartMap, String resourceid, String pre_splitDate) {
    String pre_workTime = "";
    List<Map<String, String>> pre_todaySignTime = (List<Map<String, String>>)pre_todayLineMap.get("signTime");
    if(pre_todaySignTime != null && !pre_todaySignTime.isEmpty()){
      Map<String, String> pre_todaySignMap = pre_todaySignTime.get(pre_todaySignTime.size()-1);
      String endtime = pre_todaySignMap.get("endtime");
      String endtime_across = pre_todaySignMap.get("endtime_across");
      if("1".equalsIgnoreCase(endtime_across)){
        pre_workTime = splitDate+" "+endtime+":59";
      }
      Map<String, String> first_SignMap = pre_todaySignTime.get(0);
      String workbengintime = first_SignMap.get("workbengintime");
      workTimeStartMap.put("pre_workbengintime", workbengintime);
      if(pre_todaySignTime.size() == 1){
        boolean is_flow_humanized = KQSettingsBiz.is_flow_humanized();
        if(is_flow_humanized){
          ShiftInfoBean shiftInfoBean = KQDurationCalculatorUtil.getWorkTime(resourceid, pre_splitDate,false);
          Map<String,String> shifRuleMap = Maps.newHashMap();
          KQShiftRuleInfoBiz.getShiftRuleInfo(shiftInfoBean, resourceid, shifRuleMap);
          if(!shifRuleMap.isEmpty()){
            if(shifRuleMap.containsKey("shift_beginworktime")){
              String shift_beginworktime = Util.null2String(shifRuleMap.get("shift_beginworktime"));
              if(shift_beginworktime.length() > 0){
                workTimeStartMap.put("pre_workbengintime", shift_beginworktime);
              }
            }
            if(shifRuleMap.containsKey("shift_endworktime")){
              String shift_endworktime = Util.null2String(shifRuleMap.get("shift_endworktime"));
              if(shift_endworktime.length() > 0){
                pre_workTime = shift_endworktime;
              }
            }
          }
        }
      }
    }else{
      workTimeStartMap.put("pre_isrest", "1");
    }
    return pre_workTime;
  }

  /**
   * 获取最早允许上班时间
   * @param next_todayLineMap
   * @param splitDate
   * @return
   */
  private static String getStartWorktime(Map<String, Object> next_todayLineMap,String splitDate,
      Map<String, String> workTimeStartMap) {
    String next_workTime = "";
    List<Map<String, String>> next_todaySignTime = (List<Map<String, String>>)next_todayLineMap.get("signTime");
    if(next_todaySignTime != null && !next_todaySignTime.isEmpty()){
      Map<String, String> next_todaySignMap = next_todaySignTime.get(0);
      String bengintime = next_todaySignMap.get("bengintime");
      String workbengintime = next_todaySignMap.get("workbengintime");
      String bengintime_pre_across = next_todaySignMap.get("bengintime_pre_across");
      if("1".equalsIgnoreCase(bengintime_pre_across)){
        next_workTime = splitDate+" "+bengintime+":00";
      }
      workTimeStartMap.put("next_workbengintime", workbengintime);
    }else{
      workTimeStartMap.put("next_isrest", "1");
    }
    return next_workTime;
  }

  public static void overTime4Work(String splitDate, String pre_splitDate, String resourceid,
      List<KQTaskBean> tasks, Map<String, Object> todayLineMap,
      List<Map<String, String>> todaySignTime,
      Map<String, String> workTimeStartMap) {

    //现在标准是默认取最后一个班次作为加班
    Map<String, String> todaySignMap = Maps.newHashMap();
    if(todaySignTime.size() == 1){
      todaySignMap = todaySignTime.get(0);
      overTime4WorkSign(todaySignMap,splitDate, pre_splitDate, resourceid, tasks, todayLineMap, workTimeStartMap,"");
    }else if(todaySignTime.size() > 1){
      for(int i = 0 ; i < 2 ; i++) {
        if (i == 0) {
          todaySignMap = todaySignTime.get(0);
          overTime4WorkSign(todaySignMap,splitDate, pre_splitDate, resourceid, tasks, todayLineMap, workTimeStartMap,"before");
        } else if (i == 1) {
          todaySignMap = todaySignTime.get(todaySignTime.size() - 1);
          overTime4WorkSign(todaySignMap,splitDate, pre_splitDate, resourceid, tasks, todayLineMap, workTimeStartMap,"after");
        }
      }
    }
  }

  public static void overTime4WorkSign(
      Map<String, String> todaySignMap, String splitDate, String pre_splitDate,
      String resourceid, List<KQTaskBean> tasks,
      Map<String, Object> todayLineMap,
      Map<String, String> workTimeStartMap, String signsource) {
    RecordSet rs = new RecordSet();
    KQTimesArrayComInfo kqTimesArrayComInfo = new KQTimesArrayComInfo();
    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    DateTimeFormatter fullFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    DateTimeFormatter datetimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    List<String> allWorkTime = (List<String>) todayLineMap.get("allWorkTime");
    String firstWorkTime = "";
    String lastWorkTime = "";
    if(allWorkTime != null && !allWorkTime.isEmpty()){
      firstWorkTime = allWorkTime.get(0);
      lastWorkTime = allWorkTime.get(allWorkTime.size()-1);
    }
    String yesterday = LocalDate.parse(splitDate).minusDays(1).format(dateFormatter);
    String nextday = LocalDate.parse(splitDate).plusDays(1).format(dateFormatter);

    String workbengintime = Util.null2s(todaySignMap.get("workbengintime"),"");
    String bengintime = Util.null2s(todaySignMap.get("bengintime"),"");
    //上班开始时间是否跨天到后一天
    String bengintime_across = Util.null2s(todaySignMap.get("bengintime_across"),"");
    //上班打卡开始时间是否跨天到前一天
    String bengintime_pre_across = Util.null2s(todaySignMap.get("bengintime_pre_across"),"");
    String bengintime_end = Util.null2s(todaySignMap.get("bengintime_end"),"");
    String bengintime_end_across = Util.null2s(todaySignMap.get("bengintime_end_across"),"");

    String endtime = Util.null2s(todaySignMap.get("endtime"),"");
    String endtime_across = Util.null2s(todaySignMap.get("endtime_across"),"");
    String endtime_start = Util.null2s(todaySignMap.get("endtime_start"),"");
    String endtime_start_across = Util.null2s(todaySignMap.get("endtime_start_across"),"");

    String workendtime_across = Util.null2s(todaySignMap.get("workendtime_across"),"");
    boolean isEndTimeAcross = false;
    if("1".equalsIgnoreCase(workendtime_across)) {
      isEndTimeAcross = true;
    }

    String signInDateTime = splitDate + " " +bengintime+":00";
    if("1".equalsIgnoreCase(bengintime_pre_across)) {
      signInDateTime = yesterday + " " +bengintime+":00";
    }
    if("1".equalsIgnoreCase(bengintime_across)) {
      signInDateTime = nextday + " " +bengintime+":00";
    }
    String  signInEndDateTime = "";
    if(bengintime_end.length() > 0){
      signInEndDateTime = splitDate + " " +bengintime_end+":00";
      if("1".equalsIgnoreCase(bengintime_end_across)) {
        signInEndDateTime = nextday + " " +bengintime_end+":00";
      }
    }
//    if(pre_workTime.length() > 0){
//      signInDateTime = splitDate + " " +pre_workTime+":00";
//    }
    String signOutDateTime = splitDate + " " +endtime+":59";
    if("1".equalsIgnoreCase(endtime_across)) {
      signOutDateTime = nextday + " " +endtime+":59";
    }
    String signOutBeginDateTime = "";
    if(endtime_start.length() > 0){
      signOutBeginDateTime = splitDate + " " +endtime_start+":59";
      if("1".equalsIgnoreCase(endtime_start_across)) {
        signOutBeginDateTime = nextday + " " +endtime_start+":59";
      }
    }
    String sign_signSectionTime = "";
    String sign_signSectionEndTime = "";
    String sign_signSectionBeginTime = "";
    String sign_offSignSectionTime = "";
    if(signInEndDateTime.length() == 0 && signOutBeginDateTime.length() == 0){
      //如果没设置上班后，下班前打卡
      sign_signSectionTime = signInDateTime;
      sign_offSignSectionTime = signOutDateTime;
    }else{
      if(signInEndDateTime.length() > 0){
        if(signOutBeginDateTime.length() > 0){
          //如果上班后，下班前打卡范围都做了控制
          sign_signSectionTime = signInDateTime;
          sign_signSectionEndTime = signInEndDateTime;
          sign_signSectionBeginTime = signOutBeginDateTime;
          sign_offSignSectionTime = signOutDateTime;

        }else{
          LocalDateTime onLocalDateEndTime = LocalDateTime.parse(signInEndDateTime,fullFormatter);
          //如果只是上班后打卡范围做了控制
          LocalDateTime tmp = LocalDateTime.parse(onLocalDateEndTime.plusMinutes(1).format(datetimeFormatter)+":00",fullFormatter);
          String tmp_datetime = tmp.format(fullFormatter);
          sign_signSectionTime = signInDateTime;
          sign_signSectionEndTime = signInEndDateTime;
          sign_signSectionBeginTime = tmp_datetime;
          sign_offSignSectionTime = signOutDateTime;

        }
      }else if(signOutBeginDateTime.length() > 0){
        //如果只是下班前打卡范围做了控制
        LocalDateTime offLocalDateBeginTime = LocalDateTime.parse(signOutBeginDateTime,fullFormatter);
        LocalDateTime tmp = LocalDateTime.parse(offLocalDateBeginTime.minusMinutes(1).format(datetimeFormatter)+":59",fullFormatter);
        String tmp_datetime = tmp.format(fullFormatter);
        sign_signSectionTime = signInDateTime;
        sign_signSectionEndTime = tmp_datetime;
        sign_signSectionBeginTime = signOutBeginDateTime;
        sign_offSignSectionTime = signOutDateTime;
      }
    }

    KQScheduleSignBiz kqScheduleSignBiz = new KQScheduleSignBiz.KQScheduleSignParamBuilder().resourceidParam(resourceid)
        .signSectionTimeParam(sign_signSectionTime).signSectionEndTimeParam(sign_signSectionEndTime)
        .signSectionBeginTimeParam(sign_signSectionBeginTime).offSignSectionTimeParam(sign_offSignSectionTime).build();
    Map<String,KQHrmScheduleSign> signMap = kqScheduleSignBiz.getScheduleSignInfoWithCardRange();
    if(signMap != null && !signMap.isEmpty()) {
      KQHrmScheduleSign signInTimeBean = signMap.get("signin");
      KQHrmScheduleSign signOutTimeBean = signMap.get("signout");
      if(allWorkTime.size() == 2){
        boolean is_flow_humanized = KQSettingsBiz.is_flow_humanized();
        if(is_flow_humanized){
          //一天一次打卡的情况下需要考虑个性化设置
          if(todayLineMap.containsKey("shiftRuleMap")){
            Map<String,Object> shiftRuleMap = (Map<String, Object>) todayLineMap.get("shiftRuleMap");
            if (shiftRuleMap != null && !shiftRuleMap.isEmpty() && shiftRuleMap.containsKey("ruleDetail")) {//处理人性化设置其他规则
              Map<String, Object> ruleDetail = (Map<String, Object>) shiftRuleMap.get("ruleDetail");
              if (ruleDetail != null && !ruleDetail.isEmpty()) {
                Map<String,String> shifRuleMap = KQShiftRuleInfoBiz.do4ShiftRule(ruleDetail,signInTimeBean,signOutTimeBean,allWorkTime,splitDate,nextday,resourceid);
                if(!shifRuleMap.isEmpty()){
                  if(shifRuleMap.containsKey("shift_beginworktime")){
                    String shift_beginworktime = Util.null2String(shifRuleMap.get("shift_beginworktime"));
                    firstWorkTime = shift_beginworktime;
                  }
                  if(shifRuleMap.containsKey("shift_endworktime")){
                    String shift_endworktime = Util.null2String(shifRuleMap.get("shift_endworktime"));
                    if(shift_endworktime.length() > 0){
                      lastWorkTime = shift_endworktime;
                      int lastWorkTime_index = kqTimesArrayComInfo.getArrayindexByTimes(lastWorkTime);
                      if(lastWorkTime_index >= 1440){
                        isEndTimeAcross = true;
                        lastWorkTime = kqTimesArrayComInfo.turn48to24Time(lastWorkTime);
                      }else{
                        isEndTimeAcross = false;
                      }

                    }
                  }
                }
              }
            }
          }
        }
      }
      if(signsource.length() > 0){
        if("before".equalsIgnoreCase(signsource)){
          signOutTimeBean = null;
        }
        if("after".equalsIgnoreCase(signsource)){
          signInTimeBean = null;
        }
      }
      if(signInTimeBean != null){
        String signdate = Util.null2String(signInTimeBean.getSigndate());
        String signtime= Util.null2String(signInTimeBean.getSigntime());
        String pre_bengintime = Util.null2String(workTimeStartMap.get("pre_workbengintime"));
        if(pre_bengintime.length() > 0){
          //当前是工作日，前一天是工作日的情况
          pre_bengintime = pre_bengintime+":00";
          String tmp_pre_bengintime = splitDate+" "+pre_bengintime;
          String tmp_workbengintime = splitDate+" "+workbengintime+":00";
          if(tmp_pre_bengintime.compareTo(tmp_workbengintime) > 0){
            pre_bengintime = workbengintime+":00";
          }
          if(firstWorkTime.length() > 0) {
            String tmp_firstWorkTime = splitDate + " " + firstWorkTime + ":00";
            if(tmp_pre_bengintime.compareTo(tmp_firstWorkTime) > 0){
              tmp_pre_bengintime = tmp_firstWorkTime;
              pre_bengintime = firstWorkTime + ":00";
            }
          }
          String tmp_signtime = signdate+" "+signtime;
          if(tmp_pre_bengintime.compareTo(tmp_signtime) > 0){
            if(signtime.length() > 0){
              KQTaskBean kqTaskBean = new KQTaskBean();
              kqTaskBean.setResourceId(resourceid);
              kqTaskBean.setTaskDate(pre_splitDate);
              kqTaskBean.setLastWorkTime(signtime);
              kqTaskBean.setTaskSignTime(pre_bengintime);
              kqTaskBean.setSignDate(signdate);
              kqTaskBean.setSignEndDate(splitDate);
              kqTaskBean.setTimesource("before");
              if(!tasks.contains(kqTaskBean)){
                tasks.add(kqTaskBean);
              }
            }else{
              kqLog.info("overTime4Work::signtime is null:"+signtime);
            }
          }
        }else{
          //当前是工作日，前一天是非工作日的情况
          String pre_isrest = Util.null2String(workTimeStartMap.get("pre_isrest"));
          if("1".equalsIgnoreCase(pre_isrest)){
            if(firstWorkTime.length() > 0){
              String tmp_firstWorkTime = splitDate+" "+firstWorkTime+":00";
              String tmp_signtime = signdate+" "+signtime;
              if(tmp_firstWorkTime.compareTo(tmp_signtime) > 0){
                KQTaskBean kqTaskBean = new KQTaskBean();
                kqTaskBean.setResourceId(resourceid);
                kqTaskBean.setTaskDate(pre_splitDate);
                kqTaskBean.setSignDate(splitDate);
                kqTaskBean.setSignEndDate(signdate);
                kqTaskBean.setSignInTime4Out(signtime);
                kqTaskBean.setTaskSignTime(firstWorkTime+":00");
                kqTaskBean.setTimesource("before");
                if(!tasks.contains(kqTaskBean)){
                  tasks.add(kqTaskBean);
                }
              }
            }
          }
        }
      }
      if(signOutTimeBean != null){
        String signdate = Util.null2String(signOutTimeBean.getSigndate());
        String signtime= Util.null2String(signOutTimeBean.getSigntime());

        if(lastWorkTime.length() > 0 && signtime.length() > 0){
          if(signtime.length() == 5){
            signtime += ":00";
          }
          if(lastWorkTime.length() == 5){
            lastWorkTime += ":00";
          }
          String tmpsigndatetime = signdate+" "+signtime;
          String tmpworkdatetime = splitDate+" "+lastWorkTime;
          if(isEndTimeAcross){
            tmpworkdatetime = nextday+" "+lastWorkTime;
          }
          if(tmpsigndatetime.compareTo(tmpworkdatetime) > 0){
            KQTaskBean kqTaskBean = new KQTaskBean();
            kqTaskBean.setResourceId(resourceid);
            kqTaskBean.setTaskDate(splitDate);
            kqTaskBean.setLastWorkTime(lastWorkTime);
            kqTaskBean.setTaskSignTime(signtime);
            if(isEndTimeAcross){
              kqTaskBean.setSignDate(nextday);
            }else{
              kqTaskBean.setSignDate(splitDate);
            }
            kqTaskBean.setSignEndDate(signdate);
            kqTaskBean.setTimesource("after");
            if(!tasks.contains(kqTaskBean)){
              tasks.add(kqTaskBean);
            }
          }
        }else{
          kqLog.info("overTime4Work:lastWorkTime is null :"+lastWorkTime+":signtime is null:"+signtime);
        }
      }
    }
  }
  /**
   * 针对非工作日推送加班数据
   * @param splitDate
   * @param pre_workTime 前一天是工作日，前一天工作日最后下班时间跨天了会影响到今天开始打卡时间
   * @param resourceid
   * @param tasks
   * @param next_workTime 后一天是工作日，后一天的最早上班时间跨天了，会影响到今天的结束打卡时间
   * @param workTimeStartMap
   * @param pre_splitDate
   */
  public static void overTime4NonWork(String splitDate, String pre_workTime, String resourceid,
      List<KQTaskBean> tasks, String next_workTime,
      Map<String, String> workTimeStartMap, String pre_splitDate){

    String signInTime4Out = "";
    String signTime = "";
    String bengintime = "00:00";
    String endtime = "23:59";
    String signInDateTime = splitDate + " " +bengintime+":00";
    if(pre_workTime.length() > 0){
      signInDateTime = pre_workTime;
    }
    String signOutDateTime = splitDate + " " +endtime+":59";
    if(next_workTime.length() > 0){
      signOutDateTime = next_workTime;
    }
    String signDateTimeSql = "";
    String buildSql = KQSignUtil.buildSignSql(signInDateTime, signOutDateTime);
    if(buildSql.length() > 0){
      signDateTimeSql += buildSql;
    }
    KQScheduleSignBiz kqScheduleSignBiz = new KQScheduleSignBiz.KQScheduleSignParamBuilder().resourceidParam(resourceid).
        signDateTimeSqlParam(signDateTimeSql).signDateParam(splitDate).build();
    Map<String, KQHrmScheduleSign> signMap = kqScheduleSignBiz.getScheduleSignInfo();
    if(signMap != null && !signMap.isEmpty()) {
      KQHrmScheduleSign signInTimeBean = signMap.get("signin");
      KQHrmScheduleSign signOutTimeBean =  signMap.get("signout");
      if(signInTimeBean != null){
        signInTime4Out = Util.null2String(signInTimeBean.getSigntime());
      }
      if(signOutTimeBean != null){
        signTime = Util.null2String(signOutTimeBean.getSigntime());
      }
    }

    String pre_bengintime = Util.null2String(workTimeStartMap.get("pre_workbengintime"));
    if(pre_bengintime.length() > 0) {
      pre_bengintime = pre_bengintime + ":00";
      if (pre_bengintime.compareTo(signInTime4Out) > 0) {
        //非工作日加班的话，签退数据都需要去搞一遍调休
        if(pre_bengintime.length() > 0 && signInTime4Out.length() > 0) {
          KQTaskBean kqTaskBean = new KQTaskBean();
          kqTaskBean.setResourceId(resourceid);
          kqTaskBean.setTaskDate(pre_splitDate);
          kqTaskBean.setLastWorkTime(signInTime4Out);
          kqTaskBean.setTaskSignTime(pre_bengintime);
          kqTaskBean.setSignDate(splitDate);
          kqTaskBean.setSignEndDate(splitDate);
          kqTaskBean.setTimesource("before");
          if(!tasks.contains(kqTaskBean)){
            tasks.add(kqTaskBean);
          }
        }else{
          kqLog.info("overTime4NonWork:pre_bengintime is null :"+pre_bengintime+":signInTime4Out is null:"+signInTime4Out);
        }
        if(pre_bengintime.length() > 0 && signTime.length() > 0) {
          //TODO pre_bengintime signTime 需要比较一下大小
          if(signTime.compareTo(pre_bengintime) > -1){
            KQTaskBean kqTaskBean = new KQTaskBean();
            kqTaskBean.setResourceId(resourceid);
            kqTaskBean.setTaskDate(splitDate);
            kqTaskBean.setSignDate(splitDate);
            kqTaskBean.setSignEndDate(splitDate);
            //对于非工作时段，签到就是他的最后一次下班时间
            kqTaskBean.setSignInTime4Out(pre_bengintime);
            kqTaskBean.setTaskSignTime(signTime);
            kqTaskBean.setTimesource("after");
            if(!tasks.contains(kqTaskBean)){
              tasks.add(kqTaskBean);
            }
          }
        }else{
          kqLog.info("overTime4NonWork:pre_bengintime is null :"+pre_bengintime+":signTime is null:"+signTime);
        }

      }else{
        //非工作日加班的话，签退数据都需要去搞一遍调休
        if(signInTime4Out.length() > 0 && signTime.length() > 0) {
          KQTaskBean kqTaskBean = new KQTaskBean();
          kqTaskBean.setResourceId(resourceid);
          kqTaskBean.setTaskDate(splitDate);
          kqTaskBean.setSignDate(splitDate);
          kqTaskBean.setSignEndDate(splitDate);
          //对于非工作时段，签到就是他的最后一次下班时间
          kqTaskBean.setSignInTime4Out(signInTime4Out);
		  kqTaskBean.setTimesource("after");
          kqTaskBean.setTaskSignTime(signTime);
          if(!tasks.contains(kqTaskBean)){
            tasks.add(kqTaskBean);
          }
        }else{
          kqLog.info("overTime4NonWork:signInTime4Out is null :"+signInTime4Out+":signTime is null:"+signTime);
        }
      }
    }else{
      //非工作日加班的话，签退数据都需要去搞一遍调休
      if(signInTime4Out.length() > 0 && signTime.length() > 0) {
        KQTaskBean kqTaskBean = new KQTaskBean();
        kqTaskBean.setResourceId(resourceid);
        kqTaskBean.setTaskDate(splitDate);
        kqTaskBean.setSignDate(splitDate);
        kqTaskBean.setSignEndDate(splitDate);
        //对于非工作时段，签到就是他的最后一次下班时间
        kqTaskBean.setSignInTime4Out(signInTime4Out);
        kqTaskBean.setTaskSignTime(signTime);
		    kqTaskBean.setTimesource("after");
        if(!tasks.contains(kqTaskBean)){
          tasks.add(kqTaskBean);
        }
      }else{
        kqLog.info("overTime4NonWork:signInTime4Out is null :"+signInTime4Out+":signTime is null:"+signTime);
      }
    }

  }

}
