package com.engine.kq.cmd.attendanceEvent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.api.customization.qc2757481.KqCustomUtil;
import com.engine.common.biz.AbstractCommonCommand;
import com.engine.common.entity.BizLogContext;
import com.engine.core.interceptor.CommandContext;
import com.engine.kq.biz.KQBalanceOfLeaveBiz;
import com.engine.kq.biz.KQLeaveRulesBiz;
import com.engine.kq.enums.KqSplitFlowTypeEnum;
import com.engine.kq.log.KQLog;
import com.google.common.collect.Lists;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.systeminfo.SystemEnv;

import static com.api.customization.qc2474646.Util.BalanceOfLeaveUtil.getCurrentQuarter;

/**
 * 请假校验事件
 */
public class CheckLeaveCmd extends AbstractCommonCommand<Map<String, Object>> {

    public CheckLeaveCmd(Map<String, Object> params, User user) {
        this.user = user;
        this.params = params;
    }

    @Override
    public Map<String, Object> execute(CommandContext commandContext) {
        Map<String, Object> retmap = new HashMap<String, Object>();
        KqCustomUtil kqCustomUtil = new KqCustomUtil();
        RecordSet rs = new RecordSet();
        String sql = "";
        String balanceOfLeave = "";
        try {
            String attid = Util.null2String(params.get("attid"));
            String resMap = Util.null2String(params.get("resMap"));
            String checkDurationData2json = Util.null2String(params.get("checkDurationData2json"));
            String checkRuleData2json = Util.null2String(params.get("checkRuleData2json"));
            int workflowid = Util.getIntValue(Util.null2String(params.get("workflowid")));
            int nodeid = Util.getIntValue(Util.null2String(params.get("nodeid")));
            int requestid = Util.getIntValue(Util.null2String(params.get("requestid")));
            int nodetype = Util.getIntValue(Util.null2String(params.get("nodetype")));
            int currentnodetype = Util.getIntValue(Util.null2String(params.get("currentnodetype")));
            String reduceMonth = Util.null2String(params.get("reduceMonth"));
            new KQLog().info("检测每次提交的时候，页面上的时长是多少 checkDurationData2json:" + checkDurationData2json);
            double min_duration = -1;

            // 将 JSON 字符串解析为 Map
            Map<String, String> map = JSON.parseObject(checkRuleData2json, new TypeReference<Map<String, String>>() {
            });

            // 获取键为 "0" 的时间字符串
            String timeString = map.get("0");
            // 使用字符串截取获取月份信息
            String[] parts = timeString.split("_");
            String startDate = parts[1]; // 开始日期部分
            String endDate = parts[3]; // 结束日期部分

            String startMonth = startDate.split("-")[1]; // 开始日期的月份
            String endMonth = endDate.split("-")[1]; // 结束日期的月份

            String quarter1 = getCurrentQuarter(Integer.parseInt(startMonth));
            String quarter2 = getCurrentQuarter(Integer.parseInt(endMonth));

            //==z 获取下扣除月份，先写死
            new BaseBean().writeLog("调休假提交扣除月份" + requestid + " | " + reduceMonth);

            //正式系统需要的
            String isFormal = Util.null2String(new BaseBean().getPropValue("kq_flow_formal", "isFormal"), "0");
            if ("1".equalsIgnoreCase(isFormal)) {
//      正式系统才有这个30分钟的控制
                min_duration = Util.getDoubleValue(Util.null2String(params.get("min_duration")), -1);
            }
            List<String> condition_list = Lists.newArrayList();
            new KQLog().info("resMap:" + resMap);
            new KQLog().info("checkRuleData2json:" + checkRuleData2json);
            JSONObject resObject = (JSONObject) JSON.parse(resMap);
            if (!resObject.isEmpty()) {
                Set<Entry<String, Object>> tmp_set = resObject.entrySet();
                for (Entry<String, Object> entry : tmp_set) {
                    String resourceId = entry.getKey();
                    JSONObject leavetypeObject = (JSONObject) entry.getValue();
                    if (!leavetypeObject.isEmpty()) {
                        // 带薪假存在不同释放规则，这里需要单独根据请假类型+日期 处理不同有效期和释放规则的请假
                        Map<String, String> balanceMap = new HashMap<>();
                        // 记录每一个类型的 带薪假余额的汇总list
                        Map<String, List<String>> allbalanceMap = new HashMap<>();
                        // 相同请假类型的 请假时长总和记录下
                        Map<String, String> durationMap = new HashMap<>();
                        Set<Entry<String, Object>> keave_set = leavetypeObject.entrySet();
                        for (Entry<String, Object> typeEntry : keave_set) {
                            String year_newLeaveType = typeEntry.getKey();
                            String[] year_newLeaveTypes = year_newLeaveType.split("_");
                            String newLeaveType = "";
                            String date = "";
                            if (year_newLeaveTypes.length != 2) {
                                continue;
                            }
                            date = year_newLeaveTypes[0];
                            if (date.length() == 0) {
                                date = DateUtil.getCurrentDate();
                            }
                            newLeaveType = year_newLeaveTypes[1];

                            boolean isTiaoxiu = KQLeaveRulesBiz.isTiaoXiu(newLeaveType);//是否是调休假

                            // 在这里将假期类型取出来，如果不等于当前季度，则不允许跨季度
                            if (!quarter1.equals(quarter2) && newLeaveType.equals("5")) {
                                //请假不能小于30分钟
                                retmap.put("status", "-1");
                                retmap.put("message", "请假不允许跨季度");
                                return retmap;
                            }
                            String leaveDays = Util.null2String(typeEntry.getValue());
                            boolean balanceEnable = KQLeaveRulesBiz.getBalanceEnable(newLeaveType);
                            if (balanceEnable) {
                                if ("1".equalsIgnoreCase(isFormal)) {
                                    //==z 调休假余额获取按照指定扣除月份，获取调休余额
                                    if (isTiaoxiu){
                                        balanceOfLeave = kqCustomUtil.getRestAmountMonth(newLeaveType, resourceId, DateUtil.getCurrentDate(),false,true,reduceMonth);
                                    }else {
                                        balanceOfLeave = KQBalanceOfLeaveBiz.getRestAmount(resourceId, newLeaveType, DateUtil.getCurrentDate());
                                    }
                                } else {
                                    if (isTiaoxiu){
                                        balanceOfLeave = kqCustomUtil.getRestAmountMonth(newLeaveType, resourceId, date,false,true,reduceMonth);
                                    }else {
                                        balanceOfLeave = KQBalanceOfLeaveBiz.getRestAmount(resourceId, newLeaveType, date);
                                    }
                                }
                                if (balanceMap.containsKey(newLeaveType)) {
                                    // 如果请假类型相同，记录下最大的那个假期余额
                                    String curBalance = balanceMap.get(newLeaveType);
                                    if (Util.getDoubleValue(balanceOfLeave, 0.0) > Util.getDoubleValue(curBalance, 0.0)) {
                                        balanceMap.put(newLeaveType, balanceOfLeave);
                                    }
                                    String curleaveDays = durationMap.get(newLeaveType);
                                    durationMap.put(newLeaveType, Util.null2String(
                                            Util.getDoubleValue(curleaveDays, 0.0) + Util.getDoubleValue(leaveDays, 0.0)));

                                    List<String> allbalanceList = allbalanceMap.get(newLeaveType);
                                    allbalanceList.add(balanceOfLeave);
                                } else {
                                    balanceMap.put(newLeaveType, balanceOfLeave);
                                    durationMap.put(newLeaveType, leaveDays);

                                    List<String> allbalanceList = new ArrayList<>();
                                    allbalanceList.add(balanceOfLeave);
                                    allbalanceMap.put(newLeaveType, allbalanceList);
                                }
                                double balanceOfLeaveDays = Util.getDoubleValue(balanceOfLeave);
                                if (isFreezeNodeId(workflowid, nodeid)) {
                                    double d_duration = AttendanceUtil.getFreezeDuration(newLeaveType, resourceId);
                                    if (d_duration > 0) {
                                        //以防止出现精度问题
                                        BigDecimal p1 = new BigDecimal(Double.toString(balanceOfLeaveDays));
                                        BigDecimal p2 = new BigDecimal(Double.toString(d_duration));
                                        //考虑下冻结的数据
                                        String tmp_balanceOfLeaveDays = p1.subtract(p2).toString();
                                        balanceOfLeaveDays = Util.getDoubleValue(tmp_balanceOfLeaveDays);
                                    }
                                }
                                double leaveDays_Double = Util.getDoubleValue(leaveDays);
                                if (balanceOfLeaveDays <= 0) {
                                    //可请 带薪假时长为0 不能提交
                                    retmap.put("status", "-1");
                                    retmap.put("message", SystemEnv.getHtmlLabelName(390298, user.getLanguage()));
                                    return retmap;
                                }
                                if (leaveDays_Double > balanceOfLeaveDays) {
                                    //请假天数大于带薪假时长 不能提交
                                    retmap.put("status", "-1");
                                    retmap.put("message", SystemEnv.getHtmlLabelName(390299, user.getLanguage()));
                                    return retmap;
                                }
                            }
                        }
                        if (!balanceMap.isEmpty()) {
                            for (Entry<String, String> me : balanceMap.entrySet()) {
                                String key = me.getKey();
                                double max_balance = Util.getDoubleValue(me.getValue(), 0.0);
                                double totalLeavedays = Util.getDoubleValue(durationMap.get(key), 0.0);
                                if (totalLeavedays > max_balance) {
                                    //请假天数大于带薪假时长 不能提交
                                    retmap.put("status", "-1");
                                    retmap.put("message", SystemEnv.getHtmlLabelName(390299, user.getLanguage()));
                                    return retmap;
                                }
                                ;
                                if (isFreezeNodeId(workflowid, nodeid)) {
                                    double d_duration = AttendanceUtil.getFreezeDuration(key, resourceId);
                                    if (d_duration > 0) {
                                        List<String> allbalanceList = allbalanceMap.get(key);
                                        for (String tmp : allbalanceList) {
                                            //以防止出现精度问题
                                            BigDecimal p1 = new BigDecimal(tmp);
                                            BigDecimal p2 = new BigDecimal(Double.toString(d_duration));
                                            //考虑下冻结的数据
                                            String tmp_balanceOfLeaveDays = p1.subtract(p2).toString();
                                            double tmp_max_balance = Util.getDoubleValue(tmp_balanceOfLeaveDays);
                                            if (totalLeavedays > tmp_max_balance) {
                                                //请假天数大于带薪假时长 不能提交
                                                retmap.put("status", "-1");
                                                retmap.put("message", SystemEnv.getHtmlLabelName(390299, user.getLanguage()));
                                                return retmap;
                                            }
                                            ;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (min_duration >= 0 && min_duration < 30.0) {
                    //请假不能小于30分钟
                    retmap.put("status", "-1");
                    retmap.put("message", SystemEnv.getHtmlLabelName(126255, user.getLanguage()));
                    return retmap;
                }
            }
            JSONObject checkRuleData2jsonObject = (JSONObject) JSON.parse(checkRuleData2json);
            if (!checkRuleData2jsonObject.isEmpty()) {
                CheckRuleUtil checkDuplicateUtil = new CheckRuleUtil();
                Set<Entry<String, Object>> checkRuleDataSet = checkRuleData2jsonObject.entrySet();
                checkDuplicateUtil.checkRule(checkRuleDataSet, retmap, user, "" + KqSplitFlowTypeEnum.LEAVE.getFlowtype(), requestid, currentnodetype, attid);
            }
            if (retmap.isEmpty()) {
                retmap.put("status", "1");
            }
        } catch (Exception e) {
            retmap.put("status", "-1");
            retmap.put("message", SystemEnv.getHtmlLabelName(382661, user.getLanguage()));
            writeLog(e);
        }
        return retmap;
    }

    /**
     * 只在创建节点做冻结审批的判断
     *
     * @param workflowid
     * @param nodeid
     * @return
     */
    public boolean isFreezeNodeId(int workflowid, int nodeid) {
        boolean hasFreezeNodeId = hasFreezeNodeId(workflowid);
        boolean isStartNodeId = getWFStartNodeId(workflowid) == nodeid;
        return isStartNodeId && hasFreezeNodeId;
    }

    /**
     * 获取指定流程的创建节点id
     *
     * @param workflowid
     * @return
     */
    public int getWFStartNodeId(int workflowid) {
        RecordSet rs = new RecordSet();
        StringBuffer sql = new StringBuffer("select a.nodeid from  workflow_flownode a left join workflow_nodebase b on a.nodeid = b.id")
                .append(" where a.workflowId = ").append(workflowid).append(" and (b.isFreeNode != '1' OR b.isFreeNode IS null) and b.isstart =1 ");
        rs.executeSql(sql.toString());
        return rs.next() ? rs.getInt("nodeid") : -1;
    }

    /**
     * 判断流程是否配置了冻结action
     *
     * @param workflowid
     * @return
     */
    public boolean hasFreezeNodeId(int workflowid) {
        RecordSet rs = new RecordSet();
        StringBuffer sql = new StringBuffer(" select field006 from kq_att_proc_action where field001 in (select id from kq_att_proc_set where field001 = ").append(workflowid).append(") and field002 = 'KqFreezeVacationAction' ");
        rs.executeSql(sql.toString());
        return rs.next() ? true : false;
    }

    @Override
    public BizLogContext getLogContext() {
        return null;
    }

}
