package com.engine.kq.wfset.util;

import com.engine.kq.biz.KQFlowActiontBiz;
import com.engine.kq.biz.KQLeaveRulesBiz;
import com.engine.kq.biz.KQLeaveRulesComInfo;
import com.engine.kq.enums.DurationTypeEnum;
import com.engine.kq.log.KQLog;
import com.engine.kq.wfset.bean.SplitBean;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import weaver.conn.RecordSet;
import weaver.general.Util;
import weaver.hrm.resource.ResourceComInfo;

public class KQFlowLeaveUtil {
  private KQLog kqLog = new KQLog();


  /**
   * 拆分请假数据 生成splitBeans
   * @param sqlMap
   * @param splitBeans
   * @param datetimeFormatter
   * @param workflowId
   * @param requestId
   * @param rci
   * @return
   * @throws Exception
   */
  public Map<String,String> handleKQLeaveAction(Map<String, String> sqlMap,
      List<SplitBean> splitBeans, DateTimeFormatter datetimeFormatter, int workflowId,
      int requestId, ResourceComInfo rci) throws Exception{
    KQFlowActiontBiz kqFlowActiontBiz = new KQFlowActiontBiz();
    return kqFlowActiontBiz.handleAction(sqlMap,splitBeans, datetimeFormatter, workflowId,requestId, rci,DurationTypeEnum.LEAVE);
  }


  /**
   * 请假流程单独需要赋值的数据
   * @param prefix
   * @param rs1
   * @param splitBean
   */
  public static void bean4Leave(String prefix,RecordSet rs1, SplitBean splitBean) {
    String newLeaveType = Util.null2s(rs1.getString(prefix+"newLeaveType"), "");
    String reduceMonth = Util.null2s(rs1.getString(prefix+"reduceMonth"), "");
    String minimumUnit = Util.null2s(Util.null2String(KQLeaveRulesBiz.getMinimumUnit(newLeaveType)),"-1");
    String computingMode = Util.null2s(Util.null2String(KQLeaveRulesBiz.getComputingMode(newLeaveType)),"-1");

    splitBean.setNewLeaveType(newLeaveType);
    splitBean.setDurationrule(minimumUnit);
    splitBean.setComputingMode(computingMode);
    splitBean.setReduceMonth(reduceMonth);
    if("2".equalsIgnoreCase(computingMode)){
      //只有自然日 请假才有这个排除节假日、休息日的功能
      splitBean.setFilterholidays(KQLeaveRulesBiz.getFilterHolidays(newLeaveType));
    }
    if(newLeaveType.length() > 0){
      KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
      String conversion = kqLeaveRulesComInfo.getConversion(newLeaveType);
      splitBean.setConversion(conversion);
    }
  }
}
