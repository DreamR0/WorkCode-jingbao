package com.engine.kq.cmd.attendanceEvent;

import com.alibaba.fastjson.JSON;
import com.api.customization.qc2757481.KqCustomUtil;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.KQBalanceOfLeaveBiz;
import com.engine.kq.biz.KQLeaveRulesBiz;
import com.engine.kq.biz.KQLeaveRulesComInfo;
import com.engine.kq.biz.KQUnitBiz;
import com.engine.kq.wfset.util.SplitSelectSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.systeminfo.SystemEnv;

public class GetVacationInfoCmd extends AbstractCommonCommand<Map<String, Object>> {

	public GetVacationInfoCmd(Map<String, Object> params, User user) {
		this.user = user;
		this.params = params;
	}

	@Override
	public Map<String, Object> execute(CommandContext commandContext) {
      KqCustomUtil kqCustomUtil = new KqCustomUtil();
      Map<String, Object> retmap = new HashMap<String, Object>();
		RecordSet rs = new RecordSet();
		String sql = "";
    String balanceOfLeave = "";
		try{
      //正式系统需要的
      String isFormal = Util.null2String(new BaseBean().getPropValue("kq_flow_formal", "isFormal"),"0");
      String newLeaveType = Util.null2String(params.get("newLeaveType"));
      String resourceId = Util.null2String(params.get("resourceId"));
      String fromDate = Util.null2String(params.get("fromDate"));
      String fromTime = Util.null2String(params.get("fromTime"));
      String toTime = Util.null2String(params.get("toTime"));
      String quarterStart = "";//季度开始日期
      String quarterEnd = "";//季度结束日期
      boolean balanceEnable = KQLeaveRulesBiz.getBalanceEnable(newLeaveType);
      KQLeaveRulesComInfo leaveRulesComInfo = new KQLeaveRulesComInfo();
      boolean needClear = needClear(newLeaveType,leaveRulesComInfo,fromTime,toTime);
      if(balanceEnable){
        if(fromDate.length() == 0){
          fromDate = DateUtil.getCurrentDate();
        }
        if("1".equalsIgnoreCase(isFormal)){
          fromDate = DateUtil.getCurrentDate();
        }
        balanceOfLeave = KQBalanceOfLeaveBiz.getRestAmount(resourceId, newLeaveType, fromDate);
        String minimumUnit = leaveRulesComInfo.getMinimumUnit(newLeaveType);

        String minimumUnitName = KQLeaveRulesBiz.getMinimumUnitName(minimumUnit,user.getLanguage());
        String approvalInfo = "";
        double d_duration = AttendanceUtil.getFreezeDuration(newLeaveType,resourceId);
        if(d_duration > 0){
          approvalInfo = "("+SystemEnv.getHtmlLabelName(19134, user.getLanguage())+d_duration+minimumUnitName+")";
        }
        retmap.put("vacationInfo",SystemEnv.getHtmlLabelName(10000809, Util.getIntValue(user.getLanguage()))+balanceOfLeave+minimumUnitName+approvalInfo);

        //获取对应季度的范围
        List<String> quarters = kqCustomUtil.getQuarters(fromDate);
        if (quarters.size() > 0 ){
          for (int i = 0; i < quarters.size(); i++) {
            if (i == 0){
              quarterStart = quarters.get(i);
            }else if (i == 1){
              quarterEnd = quarters.get(i);
            }
          }
        }

        if(show_split_balance()){
          String vacationInfo = "";
          String allRestAmount = "";
          String currentRestAmount = "";
          //换成季度范围
          if (quarters.size() <= 0){
            allRestAmount = KQBalanceOfLeaveBiz.getRestAmount("" + resourceId, newLeaveType, fromDate, true);
            currentRestAmount = KQBalanceOfLeaveBiz.getRestAmount("" + resourceId, newLeaveType, fromDate,false);
          }else {
            allRestAmount = KQBalanceOfLeaveBiz.getRestAmount("" + resourceId, newLeaveType, fromDate, false,true,quarterStart,quarterEnd);
            currentRestAmount = KQBalanceOfLeaveBiz.getRestAmount("" + resourceId, newLeaveType, fromDate,false,quarterStart,quarterEnd);
          }

          String beforeRestAmount = String.format("%.2f", Util.getDoubleValue(allRestAmount, 0) - Util.getDoubleValue(currentRestAmount, 0));
          String before_title = KQUnitBiz.isLeaveHour(minimumUnit) ? SystemEnv.getHtmlLabelName(513286, user.getLanguage()) : SystemEnv.getHtmlLabelName(513287, user.getLanguage());
          String current_title = KQUnitBiz.isLeaveHour(minimumUnit) ? SystemEnv.getHtmlLabelName(504395, user.getLanguage()) : SystemEnv.getHtmlLabelName(504394, user.getLanguage());
          String all_title = KQUnitBiz.isLeaveHour(minimumUnit) ? SystemEnv.getHtmlLabelName(513288, user.getLanguage()) : SystemEnv.getHtmlLabelName(513289, user.getLanguage());
          vacationInfo += before_title+":"+beforeRestAmount+"<br/>";
          vacationInfo += current_title+":"+currentRestAmount+"<br/>";
          vacationInfo += all_title+":"+allRestAmount+approvalInfo+"<br/>";
          retmap.put("vacationInfo",vacationInfo);
        }
        retmap.put("status", "1");
      }else{
        //没有休假信息的
        retmap.put("status", "2");
      }
      if(needClear){
        retmap.put("needClear", "1");
      }else{
        retmap.put("needClear", "0");
      }

		}catch (Exception e) {
			retmap.put("status", "-1");
			retmap.put("message",  SystemEnv.getHtmlLabelName(382661,user.getLanguage()));
			writeLog(e);
		}
		return retmap;
	}

  public boolean needClear(String newLeaveType,
      KQLeaveRulesComInfo leaveRulesComInfo, String fromTime, String toTime) {
    boolean needClear = false;
    String minimumUnit = leaveRulesComInfo.getMinimumUnit(newLeaveType);
    String timeselection = leaveRulesComInfo.getTimeSelection(newLeaveType);

    if(!"2".equalsIgnoreCase(timeselection)){
      if("2".equalsIgnoreCase(minimumUnit)){
        if(fromTime.equalsIgnoreCase(SplitSelectSet.forenoon_start) || fromTime.equalsIgnoreCase(SplitSelectSet.forenoon_end)){
          //表示不清空
        }else{
          needClear = true;
        }
        if(!needClear){
          if(toTime.equalsIgnoreCase(SplitSelectSet.afternoon_start) || toTime.equalsIgnoreCase(SplitSelectSet.afternoon_end)){
            //表示不清空
          }else{
            needClear = true;
          }
        }
      }else if("4".equalsIgnoreCase(minimumUnit)){
        if(fromTime.equalsIgnoreCase(SplitSelectSet.daylong_start)){
          //表示不清空
        }else{
          needClear = true;
        }
        if(!needClear){
          if(toTime.equalsIgnoreCase(SplitSelectSet.daylong_end)){
            //表示不清空
          }else{
            needClear = true;
          }
        }
      }
    }
    return needClear;
  }


  /**
   * 是否显示昨日失效的考勤按钮
   * @return
   */
  public boolean show_split_balance() {
    boolean show_split_balance = true;
    RecordSet rs = new RecordSet();
    String settingSql = "select * from KQ_SETTINGS where main_key='show_split_balance'";
    rs.executeQuery(settingSql);
    if(rs.next()){
      String main_val = rs.getString("main_val");
      if(!"1".equalsIgnoreCase(main_val)){
        show_split_balance = false;
      }
    }
    return show_split_balance;
  }

  @Override
	public BizLogContext getLogContext() {
		return null;
	}

}
