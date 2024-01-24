package com.api.customization.qc2757481;

import com.alibaba.fastjson.JSON;
import com.engine.kq.biz.KQLeaveRulesBiz;
import com.engine.kq.biz.KQLeaveRulesDetailComInfo;
import com.engine.kq.biz.KQOvertimeRulesBiz;
import com.engine.kq.biz.KQSettingsBiz;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.Util;
import weaver.hrm.User;
import weaver.hrm.resource.ResourceComInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.*;

public class KqCustomUtil {
    /**
     * 获取剩余的调休值(针对单条记录而言的，不是汇总的)
     *
     * @param restAmount
     * @param otherParam
     * @return
     */
    public String getRestAmountShow(String restAmount, String otherParam) {
        /*获取当前日期，当前时间*/
        boolean flag = false;
        try {
            Calendar today = Calendar.getInstance();
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            String[] otherParamArr = otherParam.split("\\+");
            BigDecimal baseAmount = new BigDecimal(Util.null2s(otherParamArr[0].trim(), "0"));//基数
            BigDecimal extraAmount = new BigDecimal(Util.null2s(otherParamArr[1].trim(), "0"));//额外
            BigDecimal usedAmount = new BigDecimal(Util.null2s(otherParamArr[2].trim(), "0"));//已休
            BigDecimal tiaoxiuamount = new BigDecimal(Util.null2s(otherParamArr[4].trim(), "0"));//加班生成调休

            int changeTypeCol = Util.getIntValue(otherParamArr[5].trim(), -1);//调休余额展示类型
            String effectiveDate = Util.null2s(otherParamArr[6].trim(), "0");//加班生成日期
            String resourceid = Util.null2s(otherParamArr[7].trim(), "0");//人员id

            int changeType = KQOvertimeRulesBiz.getChangeType(resourceid, effectiveDate);
            new BaseBean().writeLog("==zj==(对比)" +(changeType == changeTypeCol));
            if (changeType == changeTypeCol){
                flag = true;
            }

            BigDecimal totalAmount = baseAmount.add(extraAmount).add(tiaoxiuamount);
            String expirationDate = Util.null2String(otherParamArr[3]).trim();
            if ("".equals(expirationDate) || expirationDate.compareTo(currentDate) >= 0) {
                if (flag){
                    restAmount = totalAmount.subtract(usedAmount).setScale(2, RoundingMode.HALF_UP).toPlainString();
                }else {
                    restAmount = "0.00";
                }
            } else {
                restAmount = "0.00";
            }
        } catch (Exception e) {
            new BaseBean().writeLog("==zj==(调休明细报错)" + JSON.toJSONString(e));
        }

        new BaseBean().writeLog("==zj==(restAmount)" + JSON.toJSONString(restAmount));
        return restAmount;
    }


    /**
     * 获取调休的余额
     *
     * @param ruleId            假期类型的ID
     * @param resourceId        人员ID
     * @param searchDate        计算是否失效的指定日期
     * @param calcByCurrentDate 是根据当前日期计算还是根据指定日期计算假期是否失效：true-当前日期、false-指定日期
     * @param isAll             是获取所有年份的还是获取指定年份的：true-所有年份的、false-指定年份的
     * @param quarterStart      季度开始日期
     * @param quarterEnd        季度结束日期
     * @return
     */
    public  BigDecimal getRestAmountByDis5(String ruleId, String resourceId, String searchDate, boolean calcByCurrentDate, boolean isAll,String quarterStart,String quarterEnd) {
        BigDecimal restAmount = new BigDecimal("0");
        try {
            //获取当前日期
            Calendar today = Calendar.getInstance();
            //当前日期
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            //所属年份
            String searchYear = searchDate.substring(0, 4);
            //根据指定日期(或者根据当前日期)判断假期是否已经过了有效期
            String date = calcByCurrentDate ? currentDate : searchDate;
            String sql = "";
            RecordSet recordSet = new RecordSet();
            if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                    || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                sql = " select sum(tiaoxiuamount) as allTiaoxiuamount,sum(baseAmount) as allBaseAmount,sum(extraAmount) as allExtraAmount,sum(usedAmount) as allUsedAmount from KQ_BalanceOfLeave " +
                        " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate='' or expirationDate>='" + date + "') ";
            } else {
                sql = " select sum(tiaoxiuamount) as allTiaoxiuamount,sum(baseAmount) as allBaseAmount,sum(extraAmount) as allExtraAmount,sum(usedAmount) as allUsedAmount from KQ_BalanceOfLeave " +
                        " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate>='" + date + "') ";
            }
            //是查询所有有效的假期余额还是仅仅查询指定日期当年的
            if(KQSettingsBiz.is_balanceofleave()){//开启开关
                if (!isAll) {
                    sql += " and belongYear='" + searchYear + "'";
                }else{
                    sql += " and belongYear<='" + searchYear + "'";
                }
            }else{
                if (!isAll) {
                    sql += " and belongYear='" + searchYear + "'";
                }
            }

            //季度范围显示
            sql += "and effectiveDate between '"+quarterStart+"' and '"+quarterEnd+"'";
            new BaseBean().writeLog("==zj==(请调休假流程sql)" + JSON.toJSONString(sql));
            recordSet.executeQuery(sql);
            while (recordSet.next()) {
                BigDecimal _baseAmount = new BigDecimal(Util.null2s(recordSet.getString("allBaseAmount"), "0"));
                BigDecimal _extraAmount = new BigDecimal(Util.null2s(recordSet.getString("allExtraAmount"), "0"));
                BigDecimal _usedAmount = new BigDecimal(Util.null2s(recordSet.getString("allUsedAmount"), "0"));
                BigDecimal _tiaoxiuamount = new BigDecimal(Util.null2s(recordSet.getString("allTiaoxiuamount"), "0"));//加班生成调休

                restAmount = restAmount.add(_baseAmount).add(_tiaoxiuamount).add(_extraAmount).subtract(_usedAmount);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return restAmount;
    }

    /**
     * 获取调休的余额(指定月份)
     *
     * @param ruleId            假期类型的ID
     * @param resourceId        人员ID
     * @param searchDate        计算是否失效的指定日期
     * @param calcByCurrentDate 是根据当前日期计算还是根据指定日期计算假期是否失效：true-当前日期、false-指定日期
     * @param isAll             是获取所有年份的还是获取指定年份的：true-所有年份的、false-指定年份的
     * @param reduceMonth       扣除月份
     * @return
     */
    public  String getRestAmountMonth(String ruleId, String resourceId, String searchDate, boolean calcByCurrentDate, boolean isAll,String reduceMonth) {
        BigDecimal restAmount = new BigDecimal("0");
        KqCustomUtil kqCustomUtil = new KqCustomUtil();
        int decimalDigit = -1;
        try {
            //人员缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
             decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(ruleId, subCompanyId), 2);

            //获取当前日期
            Calendar today = Calendar.getInstance();
            //当前日期
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            //所属年份
            String searchYear = searchDate.substring(0, 4);

            //根据指定日期(或者根据当前日期)判断假期是否已经过了有效期
            String date = calcByCurrentDate ? currentDate : searchDate;
            String sql = "";
            RecordSet recordSet = new RecordSet();
            if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                    || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                sql = " select sum(tiaoxiuamount) as allTiaoxiuamount,sum(baseAmount) as allBaseAmount,sum(extraAmount) as allExtraAmount,sum(usedAmount) as allUsedAmount from KQ_BalanceOfLeave " +
                        " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate='' or expirationDate>='" + date + "') ";
            } else {
                sql = " select sum(tiaoxiuamount) as allTiaoxiuamount,sum(baseAmount) as allBaseAmount,sum(extraAmount) as allExtraAmount,sum(usedAmount) as allUsedAmount from KQ_BalanceOfLeave " +
                        " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate>='" + date + "') ";
            }
            //是查询所有有效的假期余额还是仅仅查询指定日期当年的
            if(KQSettingsBiz.is_balanceofleave()){//开启开关
                if (!isAll) {
                    sql += " and belongYear='" + searchYear + "'";
                }else{
                    sql += " and belongYear<='" + searchYear + "'";
                }
            }else{
                if (!isAll) {
                    sql += " and belongYear='" + searchYear + "'";
                }
            }

            //扣除月份范围显示
            String monthDaysFirst = "";
            String monthDaysEnd = "";
            if (!"".equals(reduceMonth)){
                List<String> monthDays = kqCustomUtil.getMonthDays(reduceMonth);
                for (int i = 0; i < monthDays.size(); i++) {
                    if (i == 0){
                        monthDaysFirst = monthDays.get(i);
                    }
                    if (i == 1){
                        monthDaysEnd = monthDays.get(i);
                    }
                }
            }
            new BaseBean().writeLog("扣除月份参数:"+reduceMonth + " | " + monthDaysFirst + " | " + monthDaysEnd);
            sql += " and effectiveDate between '"+monthDaysFirst+"' and '"+monthDaysEnd+"'";
            new BaseBean().writeLog("(请调休假流程(扣除月份)sql)" + JSON.toJSONString(sql));
            recordSet.executeQuery(sql);
            while (recordSet.next()) {
                BigDecimal _baseAmount = new BigDecimal(Util.null2s(recordSet.getString("allBaseAmount"), "0"));
                BigDecimal _extraAmount = new BigDecimal(Util.null2s(recordSet.getString("allExtraAmount"), "0"));
                BigDecimal _usedAmount = new BigDecimal(Util.null2s(recordSet.getString("allUsedAmount"), "0"));
                BigDecimal _tiaoxiuamount = new BigDecimal(Util.null2s(recordSet.getString("allTiaoxiuamount"), "0"));//加班生成调休

                restAmount = restAmount.add(_baseAmount).add(_tiaoxiuamount).add(_extraAmount).subtract(_usedAmount);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return restAmount.setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 获取指定日期的季度范围时间
     * @param fromDate
     * @return
     */
    public List<String>  getQuarters(String fromDate){
        ArrayList<String> quarterDates = new ArrayList<>();
        LocalDate currentDate = LocalDate.parse(fromDate);
        int currentYear = currentDate.getYear();
        int currentQuarter = currentDate.get(IsoFields.QUARTER_OF_YEAR);

        LocalDate startOfQuarter = LocalDate.of(currentYear, getQuarterStartMonth(currentQuarter), 1);
        LocalDate endOfQuarter = startOfQuarter.plusMonths(2).withDayOfMonth(startOfQuarter.lengthOfMonth());

        quarterDates.add(startOfQuarter.format(DateTimeFormatter.ISO_DATE));
        quarterDates.add(endOfQuarter.format(DateTimeFormatter.ISO_DATE));

        return quarterDates;
    }
    private static Month getQuarterStartMonth(int quarter) {
        switch (quarter) {
            case 1:
                return Month.JANUARY;
            case 2:
                return Month.APRIL;
            case 3:
                return Month.JULY;
            case 4:
                return Month.OCTOBER;
            default:
                throw new IllegalArgumentException("Invalid quarter: " + quarter);
        }
    }

    /**
     * 判断是否调休
     * @param params
     * @param user
     * @return
     */
    public Map<String, Object> isTiaoXiu(Map<String, Object> params,User user){
        Map<String, Object> resultMap = new HashMap<>();
        Boolean isTiaoxiu = false;
        String newLeaveType = "";
        newLeaveType =Util.null2String(params.get("newLeaveType")) ;
        if (newLeaveType.length() > 0){
            isTiaoxiu = KQLeaveRulesBiz.isTiaoXiu(newLeaveType);
        }
        resultMap.put("isTiaoxiu",isTiaoxiu);
        return resultMap;
    }

    /**
     * 批量调整调休失效时间
     * @param params
     * @param user
     * @return
     */
    public Map<String, Object> saveFailurePaid (Map<String, Object> params,User user){
        RecordSet rs = new RecordSet();
        Map<String, Object> resultMap = new HashMap<>();
        Boolean isSucess = false;
        String ids = "";//对应数据id
        String expirationDate="";//调整失效日期
        String sql = "";
        ids =Util.null2String(params.get("ids")) ;
        expirationDate =Util.null2String(params.get("expirationDate")) ;
        if (ids.length() > 0){
            String[] splits = ids.split(",");
            for (int i = 0; i < splits.length; i++) {
                sql = " update kq_balanceofleave set expirationDate='"+expirationDate+"' where id="+splits[i];
                isSucess =  rs.executeUpdate(sql);
            }
        }
        resultMap.put("isSucess",isSucess);
        return resultMap;
    }

    /**
     * 获取某月份的头尾日期
     * @param reduceMonth 年月
     * @return
     */
    public List<String> getMonthDays(String reduceMonth){
        ArrayList<String> monthList = new ArrayList<>();
        new BaseBean().writeLog("==zj==(reduceMonth)" + JSON.toJSONString(reduceMonth));

        try {
            YearMonth yearMonth = YearMonth.parse(reduceMonth);

            LocalDate firstDayOfMonth = yearMonth.atDay(1);
            LocalDate lastDayOfMonth = yearMonth.atEndOfMonth();

            monthList.add(firstDayOfMonth.format(DateTimeFormatter.ISO_DATE));//月头
            monthList.add(lastDayOfMonth.format(DateTimeFormatter.ISO_DATE));//月尾
        } catch (Exception e) {
            new BaseBean().writeLog("(获取月份头尾报错)" + e);
        }
        return monthList;
    }


}
