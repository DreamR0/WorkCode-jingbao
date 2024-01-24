package com.engine.kq.biz;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.api.customization.qc2474646.Util.BalanceOfLeaveUtil;
import com.api.customization.qc2757481.KqCustomUtil;
import com.engine.kq.entity.KQBalanceOfLeaveEntity;
import com.engine.kq.entity.KQOvertimeRulesDetailEntity;
import com.engine.kq.entity.KQUsageHistoryEntity;
import com.engine.kq.log.KQLog;
import com.engine.kq.wfset.bean.OvertimeBalanceTimeBean;
import com.google.common.collect.Maps;
import java.time.format.DateTimeFormatter;
import weaver.common.DateUtil;
import weaver.conn.RecordSet;
import weaver.general.BaseBean;
import weaver.general.TimeUtil;
import weaver.general.Util;
import weaver.hrm.HrmUserVarify;
import weaver.hrm.User;
import weaver.hrm.resource.ResourceComInfo;
import weaver.systeminfo.SystemEnv;
import weaver.hrm.moduledetach.ManageDetachComInfo;
import java.math.BigDecimal;
import weaver.hrm.company.SubCompanyComInfo;
import java.math.RoundingMode;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.api.customization.qc2474646.Util.BalanceOfLeaveUtil.getCurrentQuarter;

/**
 * 假期余额相关接口
 */
public class KQBalanceOfLeaveBiz {

    //qc2474646 这里是用来判断是否是从我的考勤进入的
    private static String isFromMyAttendance = "";


    public static String getIsFromMyAttendance() {
        return isFromMyAttendance;
    }

    public static void setIsFromMyAttendance(String isFromMyAttendance) {
        KQBalanceOfLeaveBiz.isFromMyAttendance = isFromMyAttendance;
    }

    private static KQLog logger = new KQLog();//用于记录日志信息

    public static String getBalanceOfLeave(String resourceId, String ruleId, String date) {
        return getBalanceOfLeave(resourceId, ruleId, date, true);
    }

    public static String getBalanceOfLeave(String resourceId, String ruleId, String date, boolean isAll) {
        return getBalanceOfLeave(resourceId, ruleId, date, false, isAll);
    }

    public static String getBalanceOfLeave(String resourceId, String ruleId, String searchDate, boolean calcByCurrentDate, boolean isAll) {
        return getRestAmount(resourceId, ruleId, searchDate, calcByCurrentDate, isAll);
    }

    /**
     * 获取指定人员的指定假期的有效的假期余额
     *
     * @param resourceId 指定的人员ID
     * @param ruleId     指定的假期类型的ID(表kq_leaveRules的主键ID)
     * @param date       请假日期(根据此日期计算释放规则以及判断员工假期余额是否失效)
     * @return 如果传入的指定假期类型没有开启假期余额，则返回0.00
     */
    public static String getRestAmount(String resourceId, String ruleId, String date) {
        return getRestAmount(resourceId, ruleId, date, true);
    }

    /**
     * 获取指定人员的指定假期的有效的假期余额
     *
     * @param resourceId 指定的人员ID
     * @param ruleId     指定的假期类型的ID(表kq_leaveRules的主键ID)
     * @param date       请假日期(根据此日期计算释放规则以及判断员工假期余额是否失效)
     * @param isAll      true--查询所有年份加起来的总有效余额、false--仅仅查询指定请假日期那一年的有效余额
     * @return 如果传入的指定假期没有开启假期余额，则返回0.00
     */
    public static String getRestAmount(String resourceId, String ruleId, String date, boolean isAll) {

        return getRestAmount(resourceId, ruleId, date, false, isAll);
    }

    /**
     * 获取指定人员的指定假期的有效的假期余额(季度)
     *
     * @param resourceId 指定的人员ID
     * @param ruleId     指定的假期类型的ID(表kq_leaveRules的主键ID)
     * @param date       请假日期(根据此日期计算释放规则以及判断员工假期余额是否失效)
     * @param isAll      true--查询所有年份加起来的总有效余额、false--仅仅查询指定请假日期那一年的有效余额
     * @param quarterStart      季度开始日期
     * @param quarterEnd      季度结束日期
     * @return 如果传入的指定假期没有开启假期余额，则返回0.00
     */
    public static String getRestAmount(String resourceId, String ruleId, String date, boolean isAll,String quarterStart,String quarterEnd) {

        return getRestAmount(resourceId, ruleId, date, false, isAll,quarterStart,quarterEnd);
    }
    /**
     * 获取指定人员的指定假期的有效的假期余额(季度)
     *
     * @param resourceId        指定的人员ID
     * @param ruleId            指定的假期类型的ID(表kq_leaveRules的主键ID)
     * @param searchDate        请假日期(不可为空)
     * @param calcByCurrentDate true--根据当前日期计算释放规则以及判断员工假期余额是否失效、false--根据请假日期来计算释放规则以及判断员工假期余额是否失效
     * @param isAll             true--查询所有年份加起来的总有效余额、false--仅仅查询指定请假日期那一年的有效余额
     * @return 如果传入的指定假期没有开启假期余额，则返回0.00
     */
    public static String getRestAmount(String resourceId, String ruleId, String searchDate, boolean calcByCurrentDate, boolean isAll,String quarterStart,String quarterEnd) {
        //员工的假期余额
        String balanceOfLeave = "0";
        Map<String, Object> params = new HashMap<>();
        params.put("resourceId",resourceId);
        params.put("ruleId",ruleId);
        params.put("searchDate",searchDate);
        params.put("calcByCurrentDate",calcByCurrentDate);
        params.put("isAll",isAll);
        KqCustomUtil kqCustomUtil = new KqCustomUtil();
        try {
            BigDecimal restAmount = new BigDecimal("0");
            //获取当前日期
            Calendar today = Calendar.getInstance();
            //当前日期
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            //所属年份
            String searchYear = searchDate.substring(0, 4);

            /**********************************************************************************************************/

            //假期类型缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //是否启用假期余额：0-未开启、1-开启
            int balanceEnable = Util.getIntValue(rulesComInfo.getBalanceEnable(ruleId), 0);

            /**********************************************************************************************************/

            //人员缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);

            /**********************************************************************************************************/

            //假期规则缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //余额发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按司龄+工龄自动发放
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subCompanyId), 1);
            //
            int decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(ruleId, subCompanyId), 2);

            /**********************************************************************************************************/

            //该假期没有启用假期余额，记录错误日志，返回假期余额0
            if (balanceEnable != 1) {
                logger.info("获取员工假期余额失败，此假期类型没有开启假期余额。params=" + JSONObject.toJSONString(params));
                return "0";
            }
            //传入的请假日期为空，记录错误日期，返回假期余额0
            if (searchDate.equals("")) {
                logger.info("获取员工假期余额失败，传入的请假日期为空。params=" + JSONObject.toJSONString(params));
                return "0";
            }
            //传入的指定日期格式不对，记录错误日期，返回假期余额0
            Pattern pattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2}$");
            Matcher matcher = pattern.matcher(searchDate);
            if (!matcher.matches()) {
                logger.info("获取员工假期余额失败，传入的请假日期格式不对。params=" + JSONObject.toJSONString(params));
                return "0";
            }
            //根据指定日期(或者根据当前日期)判断假期是否已经过了有效期
            String date = calcByCurrentDate ? currentDate : searchDate;

            //如果是调休，获取假期余额的逻辑会不太一样
            if (distributionMode == 5) {
                restAmount = kqCustomUtil.getRestAmountByDis5(ruleId, resourceId, searchDate, calcByCurrentDate, isAll,quarterStart,quarterEnd);
                return restAmount.setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
            }

            //计算员工假期余额总和
            RecordSet recordSet = new RecordSet();
            String sql = "select * from kq_BalanceOfLeave where resourceId=? and leaveRulesId=? and (isDelete is null or isDelete<>1) ";
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

            recordSet.executeQuery(sql, resourceId, ruleId);
            while (recordSet.next()) {
                //所属年份
                String belongYear = recordSet.getString("belongYear");
                //生效日期
                String effectiveDate = recordSet.getString("effectiveDate");
                //失效日期
                String expirationDate = recordSet.getString("expirationDate");
                //是否失效
                boolean status = getBalanceStatus(ruleId, resourceId, belongYear, date, effectiveDate, expirationDate);
                if (!status) {
                    continue;
                }

                if (distributionMode == 6) {
                    BigDecimal baseAmount = new BigDecimal(Util.null2s(recordSet.getString("baseAmount"), "0"));//混合模式下的法定年假基数
                    BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));//混合模式下的已休法定年假数
                    BigDecimal extraAmount = new BigDecimal(Util.null2s(recordSet.getString("extraAmount"), "0"));//混合模式下的额外法定年假数
                    //根据释放规则获取已释放的天数/小时数
                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "legal", date);

                    BigDecimal baseAmount2 = new BigDecimal(Util.null2s(recordSet.getString("baseAmount2"), "0"));//混合模式下的福利年假基数
                    BigDecimal usedAmount2 = new BigDecimal(Util.null2s(recordSet.getString("usedAmount2"), "0"));//混合模式下的已休福利年假数
                    BigDecimal extraAmount2 = new BigDecimal(Util.null2s(recordSet.getString("extraAmount2"), "0"));//混合模式下的额外福利年假数
                    //根据释放规则获取已释放的天数/小时数
                    baseAmount2 = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount2, "welfare", date);
                    //剩余=已释放+额外-已休
                    restAmount = restAmount.add(baseAmount.add(extraAmount).subtract(usedAmount).add(baseAmount2).add(extraAmount2).subtract(usedAmount2));
                } else {
                    BigDecimal baseAmount = new BigDecimal(Util.null2s(recordSet.getString("baseAmount"), "0"));//假期余额基数
                    BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));//已休的假期时长
                    BigDecimal extraAmount = new BigDecimal(Util.null2s(recordSet.getString("extraAmount"), "0"));//额外的假期时长
                    //根据释放规则获取已释放的天数/小时数
                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "", date);
                    //剩余=已释放+额外-已休
                    restAmount = restAmount.add(baseAmount.add(extraAmount).subtract(usedAmount));
                }
            }
            balanceOfLeave = restAmount.setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.info("获取员工假期余额失败。params=" + JSONObject.toJSONString(params));
        }
        return balanceOfLeave;
    }

    /**
     * 获取指定人员的指定假期的有效的假期余额
     *
     * @param resourceId        指定的人员ID
     * @param ruleId            指定的假期类型的ID(表kq_leaveRules的主键ID)
     * @param searchDate        请假日期(不可为空)
     * @param calcByCurrentDate true--根据当前日期计算释放规则以及判断员工假期余额是否失效、false--根据请假日期来计算释放规则以及判断员工假期余额是否失效
     * @param isAll             true--查询所有年份加起来的总有效余额、false--仅仅查询指定请假日期那一年的有效余额
     * @return 如果传入的指定假期没有开启假期余额，则返回0.00
     */
    public static String getRestAmount(String resourceId, String ruleId, String searchDate, boolean calcByCurrentDate, boolean isAll) {
        //员工的假期余额
        String balanceOfLeave = "0";
        Map<String, Object> params = new HashMap<>();
        params.put("resourceId",resourceId);
        params.put("ruleId",ruleId);
        params.put("searchDate",searchDate);
        params.put("calcByCurrentDate",calcByCurrentDate);
        params.put("isAll",isAll);
        try {
            BigDecimal restAmount = new BigDecimal("0");
            //获取当前日期
            Calendar today = Calendar.getInstance();
            //当前日期
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            //所属年份
            String searchYear = searchDate.substring(0, 4);

            /**********************************************************************************************************/

            //假期类型缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //是否启用假期余额：0-未开启、1-开启
            int balanceEnable = Util.getIntValue(rulesComInfo.getBalanceEnable(ruleId), 0);

            /**********************************************************************************************************/

            //人员缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);

            /**********************************************************************************************************/

            //假期规则缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //余额发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按司龄+工龄自动发放
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subCompanyId), 1);
            //
            int decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(ruleId, subCompanyId), 2);

            /**********************************************************************************************************/

            //该假期没有启用假期余额，记录错误日志，返回假期余额0
            if (balanceEnable != 1) {
                logger.info("获取员工假期余额失败，此假期类型没有开启假期余额。params=" + JSONObject.toJSONString(params));
                return "0";
            }
            //传入的请假日期为空，记录错误日期，返回假期余额0
            if (searchDate.equals("")) {
                logger.info("获取员工假期余额失败，传入的请假日期为空。params=" + JSONObject.toJSONString(params));
                return "0";
            }
            //传入的指定日期格式不对，记录错误日期，返回假期余额0
            Pattern pattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2}$");
            Matcher matcher = pattern.matcher(searchDate);
            if (!matcher.matches()) {
                logger.info("获取员工假期余额失败，传入的请假日期格式不对。params=" + JSONObject.toJSONString(params));
                return "0";
            }
            //根据指定日期(或者根据当前日期)判断假期是否已经过了有效期
            String date = calcByCurrentDate ? currentDate : searchDate;

            //如果是调休，获取假期余额的逻辑会不太一样
            new BaseBean().writeLog("==zj==(distributionMode)" + JSON.toJSONString(distributionMode));
            if (distributionMode == 5) {
                restAmount = getRestAmountByDis5(ruleId, resourceId, searchDate, calcByCurrentDate, isAll);
                return restAmount.setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
            }

            //计算员工假期余额总和
            RecordSet recordSet = new RecordSet();
            String sql = "select * from kq_BalanceOfLeave where resourceId=? and leaveRulesId=? and (isDelete is null or isDelete<>1) ";
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

            recordSet.executeQuery(sql, resourceId, ruleId);
            while (recordSet.next()) {
                //所属年份
                String belongYear = recordSet.getString("belongYear");
                //生效日期
                String effectiveDate = recordSet.getString("effectiveDate");
                //失效日期
                String expirationDate = recordSet.getString("expirationDate");
                //是否失效
                boolean status = getBalanceStatus(ruleId, resourceId, belongYear, date, effectiveDate, expirationDate);
                if (!status) {
                    continue;
                }

                if (distributionMode == 6) {
                    BigDecimal baseAmount = new BigDecimal(Util.null2s(recordSet.getString("baseAmount"), "0"));//混合模式下的法定年假基数
                    BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));//混合模式下的已休法定年假数
                    BigDecimal extraAmount = new BigDecimal(Util.null2s(recordSet.getString("extraAmount"), "0"));//混合模式下的额外法定年假数
                    //根据释放规则获取已释放的天数/小时数
                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "legal", date);

                    BigDecimal baseAmount2 = new BigDecimal(Util.null2s(recordSet.getString("baseAmount2"), "0"));//混合模式下的福利年假基数
                    BigDecimal usedAmount2 = new BigDecimal(Util.null2s(recordSet.getString("usedAmount2"), "0"));//混合模式下的已休福利年假数
                    BigDecimal extraAmount2 = new BigDecimal(Util.null2s(recordSet.getString("extraAmount2"), "0"));//混合模式下的额外福利年假数
                    //根据释放规则获取已释放的天数/小时数
                    baseAmount2 = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount2, "welfare", date);
                    //剩余=已释放+额外-已休
                    restAmount = restAmount.add(baseAmount.add(extraAmount).subtract(usedAmount).add(baseAmount2).add(extraAmount2).subtract(usedAmount2));
                } else {
                    BigDecimal baseAmount = new BigDecimal(Util.null2s(recordSet.getString("baseAmount"), "0"));//假期余额基数
                    BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));//已休的假期时长
                    BigDecimal extraAmount = new BigDecimal(Util.null2s(recordSet.getString("extraAmount"), "0"));//额外的假期时长
                    //根据释放规则获取已释放的天数/小时数
                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "", date);
                    //剩余=已释放+额外-已休
                    restAmount = restAmount.add(baseAmount.add(extraAmount).subtract(usedAmount));
                }
            }
            balanceOfLeave = restAmount.setScale(decimalDigit, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.info("获取员工假期余额失败。params=" + JSONObject.toJSONString(params));
        }
        return balanceOfLeave;
    }

    /**
     * 获取指定人员的指定假期类型的指定年份在指定日期的可用余额(假期基数*释放比例)
     *
     * @param resourceId     指定的人员ID
     * @param ruleId         指定的假期类型ID(对应于数据库表kq_leaveRules的主键ID)
     * @param belongYear     指定的所属年份(格式yyyy)
     * @param baseAmount     假期基数
     * @param legalOrWelfare 是计算[法定年假]还是计算[福利年假]：legal-法定年假、welfare-福利年假
     * @param date           指定日期(获取的就是此日期的假期可用余额)
     * @return
     */
    public static BigDecimal getCanUseAmount(String resourceId, String ruleId, String belongYear, BigDecimal baseAmount, String legalOrWelfare, String date) {
        BigDecimal canUseAmount = new BigDecimal("0");
        try {
            //人员缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);
            //创建日期
            String createDate = resourceComInfo.getCreatedate(resourceId);
            //入职日期
            String companyStartDate = resourceComInfo.getCompanyStartDate(resourceId);
            //没有维护入职日期时取创建日期
            companyStartDate = companyStartDate.equals("") ? createDate : companyStartDate;
            //参加工作日期
            String workStartDate = resourceComInfo.getWorkStartDate(resourceId);
            //没有维护参加工作日期时取创建日期
            workStartDate = workStartDate.equals("") ? createDate : workStartDate;

            /*********************************************************************************/

            //指定日期--用于计算已释放天数的日期
            Calendar searchDate = DateUtil.getCalendar(date);
            //指定日期的年份
            String searchYear = Util.add0(searchDate.get(Calendar.YEAR), 4);
            //指定日期那年一共多少天
            BigDecimal actualMaximum = new BigDecimal("" + searchDate.getActualMaximum(Calendar.DAY_OF_YEAR));
            //指定日期是那年的第几天
            BigDecimal dayOfYear = new BigDecimal("" + searchDate.get(Calendar.DAY_OF_YEAR));
            //指定日期是那年的第几月
            BigDecimal monthOfYear = new BigDecimal("" + (searchDate.get(Calendar.MONTH) + 1));

            /*********************************************************************************/

            //获取此假期类型下，该人员所在分部对应的假期规则
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //余额发放方式
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subCompanyId), 1);
            //释放规则：0-不限制、1-按天释放、2-按月释放
            int releaseRule = Util.getIntValue(detailComInfo.getReleaseRule(ruleId, subCompanyId), 0);
            //小数位数
            int decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(ruleId, subCompanyId), 2);

            /*********************************************************************************/

            //这几种发放方式不支持释放规则
            if (distributionMode == 5 || distributionMode == 7|| distributionMode == 8) {
                return baseAmount;
            }

            //指定日期小于入职日期时，释放出来的假期余额应为0(无人提出此要求，暂时不加此限制)

            /*********************************************************************************/

            //这一年什么时候开始想有假期的
            Calendar calendar = null;

            //如果指定日期的年份大于假期余额的所属年份，则代表假期基数已经全部释放
            if (searchYear.compareTo(belongYear) > 0) {
                return baseAmount;
            } else if (searchYear.compareTo(belongYear) < 0) {
                //如果指定日期的年份小于假期余额的所属年份，则代表假期基数还没有开始释放
                if (releaseRule != 0) {
                    return new BigDecimal("0");
                }
            } else if (searchYear.compareTo(belongYear) == 0) {
                if (distributionMode == 6) {
                    calendar = getReleaseDateByDis6(resourceId, ruleId, belongYear, legalOrWelfare);
                } else {
                    calendar = getReleaseDate(resourceId, ruleId, belongYear);
                }
            }

            /****************************************************************************************/

            /* 释放规则 start */
            if (releaseRule == 0) {
                canUseAmount = baseAmount;
            } else if (releaseRule == 1) {
                BigDecimal dateIndex = new BigDecimal("" + (calendar.get(Calendar.DAY_OF_YEAR) - 1));
                canUseAmount = baseAmount.multiply(dayOfYear.subtract(dateIndex)).divide(actualMaximum.subtract(dateIndex), decimalDigit, RoundingMode.HALF_UP);
            } else if (releaseRule == 2) {
                BigDecimal monthIndex = new BigDecimal("" + calendar.get(Calendar.MONTH));
                canUseAmount = baseAmount.multiply(monthOfYear.subtract(monthIndex)).divide(new BigDecimal("12").subtract(monthIndex), decimalDigit, RoundingMode.HALF_UP);
            }
            /* 释放规则 end */
            //如果释放为负数，就修改为0
            int i=canUseAmount.compareTo(BigDecimal.ZERO);
            if(i < 0){
                canUseAmount = new BigDecimal("0");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return canUseAmount;
    }

    /**
     * 获取失效日期
     *
     * @param ruleId         指定的假期类型ID(对应于数据库表kq_leaveRules的主键ID)
     * @param resourceId     指定的人员ID
     * @param belongYear     指定的所属年份(格式yyyy)
     * @param effectiveDate  生效日期
     * @param expirationDate 失效日期(待计算返回的)
     * @return
     */
    public static String getExpirationDate(String ruleId, String resourceId, String belongYear, String effectiveDate, String expirationDate) {
        try {
            //人力资源缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //入职日期
            String companyStartDate = resourceComInfo.getCompanyStartDate(resourceId);
            //创建日期
            String createDate = resourceComInfo.getCreatedate(resourceId);
            //如果入职日期为空，默认取创建日期
            companyStartDate = companyStartDate.equals("") ? createDate : companyStartDate;
            //分部ID
            String subcompanyId = resourceComInfo.getSubCompanyID(resourceId);

            //假期规则缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //有效期规则：0-不限制、1-按自然年失效、2-按入职日期起12个月、3-自定义次年失效日期、4-按天数失效、5-按季度失效、6-按月数失效
            int validityRule = Util.getIntValue(detailComInfo.getValidityRule(ruleId, subcompanyId), 0);
            //自定义有效天数([validityRule=4]时有效)
            int effectiveDays = Util.getIntValue(detailComInfo.getEffectiveDays(ruleId, subcompanyId), 0);
            //自定义有效月数([validityRule=6]时有效)
            int effectiveMonths = Util.getIntValue(detailComInfo.getEffectiveMonths(ruleId, subcompanyId), 0);
            //自定义次年失效的月份([validityRule=3]时有效)
            String expirationMonth = detailComInfo.getExpirationMonth(ruleId, subcompanyId);
            //自定义次年失效的日期([validityRule=3]时有效)
            String expirationDay = detailComInfo.getExpirationDay(ruleId, subcompanyId);
            //允许延长有效期：0--不允许、1--允许
            int extensionEnable = Util.getIntValue(detailComInfo.getExtensionEnable(ruleId, subcompanyId), 0);
            //允许延长的有效天数
            int extendedDays = Util.getIntValue(detailComInfo.getExtendedDays(ruleId, subcompanyId));
            //获取当前日期
            String currentDate = DateUtil.getCurrentDate();
            //如果生效日期为空,默认取当前日期
            effectiveDate = effectiveDate.equals("") ? currentDate : effectiveDate;

            if (validityRule == 0) {
                //不限制有效期，默认永久有效(永久有效时有效日期默认为2222-12-31)
                expirationDate = "2222-12-31";
            } else if (validityRule == 1) {
                //按自然年（1月1日-12月31日）
                if (extensionEnable == 1) {
                    //允许延长有效期
                    expirationDate = DateUtil.addDate(belongYear + "-12-31", extendedDays);
                } else {
                    expirationDate = belongYear + "-12-31";
                }
            } else if (validityRule == 2) {
                //按入职日期起12个月
                if (companyStartDate.equals("")) {
                    expirationDate = effectiveDate;
                } else if (extensionEnable == 1) {
                    /*允许延长有效期*/
                    expirationDate = DateUtil.addDate((Util.getIntValue(belongYear) + 1) + companyStartDate.substring(4), extendedDays);
                } else {
                    expirationDate = (Util.getIntValue(belongYear) + 1) + companyStartDate.substring(4);
                }
            } else if (validityRule == 3) {
                /*自定义次年失效日期*/
                if (extensionEnable == 1) {
                    /*允许延长有效期*/
                    expirationDate = DateUtil.addDate((Util.getIntValue(belongYear) + 1) + "-" + expirationMonth + "-" + expirationDay, extendedDays);
                } else {
                    expirationDate = (Util.getIntValue(belongYear) + 1) + "-" + expirationMonth + "-" + expirationDay;
                }
            } else if (validityRule == 4) {
                //按天数失效
                if(expirationDate.length() > 0){

                }else{
                    expirationDate = DateUtil.addDate(effectiveDate, effectiveDays);
                }
            } else if (validityRule == 5) {
                //按季度失效
                expirationDate = DateUtil.getLastDayOfQuarter(effectiveDate);
                if (extensionEnable == 1) {
                    /*允许延长有效期*/
                    expirationDate = DateUtil.addDate(expirationDate, extendedDays);
                }
            } else if (validityRule == 6){
                Calendar calendar = DateUtil.addMonth(DateUtil.getCalendar(effectiveDate), effectiveMonths - 1);
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                String dateStr = sdf.format(calendar.getTime());
                //按月数失效
                expirationDate = DateUtil.getLastDayOfMonth(dateStr);
                if (extensionEnable == 1) {
                    /*允许延长有效期*/
                    expirationDate = DateUtil.addDate(expirationDate, extendedDays);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return expirationDate;
    }
    /**
     * 获取育儿家失效日期
     *
     * @param ruleId         指定的假期类型ID(对应于数据库表kq_leaveRules的主键ID)
     * @param resourceId     指定的人员ID
     * @param belongYear     指定的所属年份(格式yyyy)
     * @param effectiveDate  生效日期
     * @param searchDate 查询搜索日期
     * @return
     */
    public static boolean getParentalExpirationDate(String ruleId, String resourceId, String belongYear, String effectiveDate, String searchDate) {
        boolean status = false;
        try {
            //人力资源缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subcompanyId = resourceComInfo.getSubCompanyID(resourceId);
            //假期规则缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //有效期规则：0-不限制、1-按自然年失效、2-按入职日期起12个月、3-自定义次年失效日期、4-按天数失效、5-按季度失效、6-按月数失效
            int validityRule = Util.getIntValue(detailComInfo.getValidityRule(ruleId, subcompanyId), 0);
            //自定义有效天数([validityRule=4]时有效)
            if (validityRule == 0) {
                //不限制有效期，默认永久有效(永久有效时有效日期默认为2222-12-31)
                return true;
            } else {
                ArrayList<String> listDates = getParentalLeaveDate(resourceId);
                for (int i = 0; i < listDates.size(); i++) {
                    String dateOfBirth = listDates.get(i);
                    String baseAmountReleaseDate = belongYear + dateOfBirth.substring(4);
                    int ageLimit = getAgeLimit(dateOfBirth, baseAmountReleaseDate);
                    if (ageLimit >= 0 && ageLimit <= 2) {
                        String expirationDate = (Util.getIntValue(belongYear) + 1) + dateOfBirth.substring(4);
                        if (expirationDate.compareTo(searchDate) >= 0) {
                            status = true;
                            break;
                        } else {
                            status = false;
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * 判断假期余额是否有效
     *
     * @param ruleId         指定的假期类型ID(对应于数据库表kq_leaveRules的主键ID)
     * @param resourceId     指定的人员ID
     * @param belongYear     指定的所属年份(格式yyyy)
     * @param searchDate     指定日期(根据此日期判断假期余额是否有效)
     * @param effectiveDate  数据库中存放的生效日期
     * @param expirationDate 数据库中存放的失效日期
     * @return
     */
    public static boolean getBalanceStatus(String ruleId, String resourceId, String belongYear, String searchDate, String effectiveDate, String expirationDate) {
        boolean status = true;
        try {
            //育儿假
            if (KQLeaveRulesBiz.isLeaveOfParental(ruleId)) {
                return getParentalExpirationDate(ruleId, resourceId, belongYear, effectiveDate, searchDate);
            }
            if (!KQLeaveRulesBiz.isTiaoXiu(ruleId)) {
                expirationDate = getExpirationDate(ruleId, resourceId, belongYear, effectiveDate, expirationDate);
            }
            if (!expirationDate.equals("") && expirationDate.compareTo(searchDate)<0) {
                status = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return status;
    }

    /**
     * 保存加班时长转换成的调休时长，当加班时长的单位与调休单位不一致时根据【加班单位】中的日折算时长转换
     *
     * @param resourceId         指定人员ID(根据人员ID判断属于哪个考勤组，根据考勤组获取加班规则ID)
     * @param date               指定日期(根据指定日期判断是节假日还是工作日还是休息日、日期格式yyyy-MM-dd)
     * @param durationOfOvertime 加班时长
     * @param type               加班时长单位：0-分钟、1-小时、2-天
     *                           单位可为空(为空则默认取【加班单位】中设置的最小加班单位)
     * @param hoursOfworkday     指定日期的工作时长(只有当指定日期为工作日的时候才有工作时长)
     *                           当最小加班单位与调休单位不一致时，且指定日期为工作日时使用此工作时长进行折算
     * @param requestId          加班流程的requestId(用于使用记录中可追溯至加班流程)
     * @param fromCardOrFlow     是打卡数据还是审批数据：1--打卡、2--流程
     * @param fromDateDb         加班流程表单中的加班开始日期
     * @return
     */
    public static boolean saveTimeOfLeave(String resourceId, String date, String durationOfOvertime, String type, String hoursOfworkday, String requestId, String fromCardOrFlow, String fromDateDb) {
        //加班转调休是否成功
        boolean flag = true;
        try {
            String tiaoxiuId = addExtraAmountByDis5(resourceId,date,durationOfOvertime,type,hoursOfworkday,requestId,fromCardOrFlow,fromDateDb,null);
            flag = Util.getIntValue(tiaoxiuId, -1) > 0;
        } catch (Exception e) {
            new BaseBean().writeLog(e);
        }
        return flag;
    }

    /**
     * 保存加班时长转换成的调休时长，当加班时长的单位与调休单位不一致时根据【加班单位】中的日折算时长转换
     *
     * @param resourceId         指定人员ID(根据人员ID判断属于哪个考勤组，根据考勤组获取加班规则ID)
     * @param date               指定日期(根据指定日期判断是节假日还是工作日还是休息日、日期格式yyyy-MM-dd)
     * @param durationOfOvertime 加班时长
     * @param type               加班时长单位：0-分钟、1-小时、2-天
     *                           单位可为空(为空则默认取【加班单位】中设置的最小加班单位)
     * @param hoursOfworkday     指定日期的工作时长(只有当指定日期为工作日的时候才有工作时长)
     *                           当最小加班单位与调休单位不一致时，且指定日期为工作日时使用此工作时长进行折算
     * @param requestId          加班流程的requestId(用于使用记录中可追溯至加班流程)
     * @param fromCardOrFlow     是打卡数据还是审批数据：1--打卡、2--流程
     * @param fromDateDb         加班流程表单中的加班开始日期
     * @return
     */
    public static String addExtraAmountByDis5(String resourceId, String date, String durationOfOvertime, String type, String hoursOfworkday, String requestId, String fromCardOrFlow, String fromDateDb,Map<String,Object> otherParam) {
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("resourceId", resourceId);
        params.put("date", date);
        params.put("durationOfOvertime", durationOfOvertime);
        params.put("type", type);
        params.put("hoursOfworkday", hoursOfworkday);
        params.put("requestId", requestId);
        params.put("fromCardOrFlow", fromCardOrFlow);
        params.put("fromDateDb", fromDateDb);
        //加班转的调休明细ID(KQ_BalanceOfLeave的ID)
        String tiaoxiuId = "-1";
        try {
            String workflowid = "-1";
            RecordSet rs = new RecordSet();
            if(requestId.length() > 0 && Util.getIntValue(requestId) > 0){
                String workflow_sql = "select workflowid from workflow_requestbase where requestid = ? ";
                rs.executeQuery(workflow_sql, requestId);
                if(rs.next()){
                    workflowid = Util.null2s(rs.getString("workflowid"),"-1");
                }
            }
            //加班转调休是否成功
            boolean flag = false;
            //人员信息缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //创建日期
            String createDate = Util.null2String(resourceComInfo.getCompanyStartDate(resourceId));
            //入职日期
            String companyStartDate = Util.null2String(resourceComInfo.getCompanyStartDate(resourceId));
            //如果入职日期为空，则取创建日期
            companyStartDate = "".equals(companyStartDate) ? createDate : companyStartDate;
            //分部ID
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);
            //日期类型：1-节假日、2-工作日、3-休息日(节假日设置的优先级高于考勤组中的设置)
            int changeType = KQOvertimeRulesBiz.getChangeType(resourceId, date);
            //获取当前日期，当前时间
            Calendar today = Calendar.getInstance();
            //当前日期
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            //当前时间
            String currentTime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                    Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                    Util.add0(today.get(Calendar.SECOND), 2);

            /****************************************************************************************************************/

            //假期类型的缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //[调休]的假期类型的ID
            String leaveRulesId = "";
            //找到[调休]的假期类型ID
            rulesComInfo.setTofirstRow();
            while (rulesComInfo.next()) {
                if (KQLeaveRulesBiz.isTiaoXiu(rulesComInfo.getId())) {
                    if("1".equals(rulesComInfo.getIsEnable())){
                        leaveRulesId = rulesComInfo.getId();
                        break;
                    }
                }
            }
            //[调休]的假期类型的最小请假单位：1-按天、2-按半天、3-按小时、4-按整天
            int minimumUnit = Util.getIntValue(rulesComInfo.getMinimumUnit(leaveRulesId), -1);
            //假期规则的缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //有效期规则：0-永久有效、1-按自然年（1月1日-12月31日）、2-按入职日期起12个月、3-自定义次年失效日期、4-按天数失效
            int validityRule = Util.getIntValue(detailComInfo.getValidityRule(leaveRulesId, subCompanyId));
            //保留几位小数
            int decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(leaveRulesId, subCompanyId), 2);

            /*如果没有设置调休的假期类型，则直接退出方法*/
            if ("".equals(leaveRulesId)) {
                logger.info("未设置调休的假期类型或未调休的假期类型下未设置改人员所在分部可用的假期规则。params=" + JSONObject.toJSONString(params));
                return "-1";
            }
            /*【调休】的假期规则的最小请假单位有误时，记录错误日志，直接返回*/
            if (minimumUnit < 1 || minimumUnit > 6) {
                logger.info("调休的请假规则的最小请假单位有误。params=" + JSONObject.toJSONString(params));
                return "-1";
            }
            /*【调休】的假期规则的有效期规则有误时，记录错误日志，直接返回*/
            if (validityRule < 0 || validityRule > 6) {
                logger.info("调休的请假规则的有效期规则有误。params=" + JSONObject.toJSONString(params));
                return "-1";
            }

            /****************************************************************************************************************/

            //所属年份
            String belongYear = date.substring(0, 4);
            //失效日期
            String expirationDate = "";
            //根据有效期规则获取有效日期
            expirationDate = getExpirationDate(leaveRulesId, resourceId, belongYear, date, expirationDate);
            //qc2474646 调休余额如果按月数时效，失效时间则为次月3号
            if (validityRule == 6){
                BalanceOfLeaveUtil balanceOfLeaveUtil = new BalanceOfLeaveUtil();
                expirationDate = balanceOfLeaveUtil.setExpirationDate(expirationDate);
                new BaseBean().writeLog("==zj==(假期余额失效日期)" + expirationDate);
            }

            /****************************************************************************************************************/

            //最小加班单位：1-按天、2-按半天、3-按小时、4-按整天、5-按半小时加班、6-整小时
            int minimumUnitOfOvertime = -1;
            //日折算时长
            double hoursToDay = -1.00;

            String sql = "select * from kq_OvertimeUnit where (isDelete is null or isDelete !=1) and id=1";
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(sql);
            if (recordSet.next()) {
                minimumUnitOfOvertime = Util.getIntValue(recordSet.getString("minimumUnit"), -1);
                hoursToDay = Util.getDoubleValue(recordSet.getString("hoursToDay"), 8.00);
            }
            /*【加班单位】的相关设置获取有误时，记录错误日志，直接返回*/
            if (minimumUnitOfOvertime == -1 || hoursToDay < 0) {
                logger.info("获取到的加班单位的相关设置有误。params=" + JSONObject.toJSONString(params));
                return "-1";
            }

            /**
             * 加班单位与调休单位不一致，实现转换
             */
            if (changeType == 2) {
                if (hoursOfworkday.equals("")) {
                    logger.info("指定日期为工作日，但未传入工作日的工作时长。params=" + JSONObject.toJSONString(params));
                }
                //当最小加班单位与调休单位不一致时，且指定日期为工作日时使用此工作时长进行折算
                hoursToDay = Util.getDoubleValue(hoursOfworkday, hoursToDay);
            }
            BigDecimal _hoursToDay = new BigDecimal("" + hoursToDay);
            //把加班时长只按照分钟先处理一下
            BigDecimal min_durationOfOvertime = new BigDecimal(Util.null2s(durationOfOvertime, "0"));
            if (type.equals("0")) {
            } else if (type.equals("1")) {
                //加班时长类型为小时---将加班分钟时长*60
                min_durationOfOvertime = min_durationOfOvertime.multiply(new BigDecimal("60"));
            } else if (type.equals("2")) {
                //加班时长类型为天----将加班分钟时长*一天加班小时数*60
                min_durationOfOvertime = min_durationOfOvertime.multiply(_hoursToDay).multiply(new BigDecimal("60"));
            } else if (type.equals("")) {
            }
            BigDecimal _durationOfOvertime = new BigDecimal(Util.null2s(durationOfOvertime, "0"));
            if (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4) {//调休单位为天
                if (type.equals("0")) {
                    //加班时长类型为分钟、调休单位为天----将加班时长转换为天
                    _durationOfOvertime = _durationOfOvertime.divide(new BigDecimal("60").multiply(_hoursToDay), 5, RoundingMode.HALF_UP);
                } else if (type.equals("1")) {
                    //加班时长类型为小时、调休单位为天----将加班时长转换为天
                    _durationOfOvertime = _durationOfOvertime.divide(_hoursToDay, 5, RoundingMode.HALF_UP);
                } else if (type.equals("2")) {
                    //加班时长类型为天、调休单位为天----不作转换
                } else if (type.equals("")) {
                    //加班时长类型为空，取【加班单位】中设置的最小加班单位
                    if (minimumUnitOfOvertime == 3 || minimumUnitOfOvertime== 5 || minimumUnitOfOvertime== 6) {
                        //最小加班单位为小时、调休单位为天----将加班时长转换为天
                        _durationOfOvertime = _durationOfOvertime.divide(_hoursToDay, 5, RoundingMode.HALF_UP);
                    } else {
                        //最小加班单位为天、调休单位为天----不作转换
                    }
                }
            } else if (KQUnitBiz.isLeaveHour(minimumUnit+"")) {//调休单位为小时
                if (type.equals("0")) {
                    //加班时长类型为分钟、调休单位为小时----将加班时长转换为小时
                    _durationOfOvertime = _durationOfOvertime.divide(new BigDecimal("60"), 5, RoundingMode.HALF_UP);
                } else if (type.equals("1")) {
                    //加班时长类型为小时、调休单位为小时----不作转换
                } else if (type.equals("2")) {
                    //加班时长类型为天、调休单位为小时----将加班时长转换为小时
                    _durationOfOvertime = _durationOfOvertime.multiply(_hoursToDay).setScale(5, RoundingMode.HALF_UP);
                } else if (type.equals("")) {
                    //加班时长类型为空，取【加班单位】中设置的最小加班单位
                    if (minimumUnitOfOvertime == 3 || minimumUnitOfOvertime== 5 || minimumUnitOfOvertime== 6) {
                        //最小加班单位为小时、调休单位为小时----不作转换
                    } else {
                        //最小加班单位为天、调休单位为小时----将加班时长转换为小时
                        _durationOfOvertime = _durationOfOvertime.multiply(_hoursToDay).setScale(5, RoundingMode.HALF_UP);
                    }
                }
            }
            logger.info("记录一下【最小加班单位】和调休的【最小请假单位】。params=" + JSONObject.toJSONString(params));

            /*员工假期余额变更记录*/
            KQUsageHistoryBiz usageHistoryBiz = new KQUsageHistoryBiz();
            KQUsageHistoryEntity usageHistoryEntity = new KQUsageHistoryEntity();
            List<KQUsageHistoryEntity> usageHistoryEntityList = new ArrayList<KQUsageHistoryEntity>();

            Map<String,Integer> changeTypeMap = Maps.newHashMap();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            Map<String, KQOvertimeRulesDetailEntity> overRulesDetailMap = Maps.newHashMap();
            Map<String,List<String[]>> restTimeMap = Maps.newHashMap();
            Map<String,Integer> computingModeMap = Maps.newHashMap();
            KQOverTimeRuleCalBiz kqOverTimeRuleCalBiz = new KQOverTimeRuleCalBiz();
            kqOverTimeRuleCalBiz.getOverTimeDataMap(resourceId, date, date, dateFormatter,changeTypeMap,overRulesDetailMap,restTimeMap,computingModeMap);

            Map<String,Object> overtimeLogMap = null;
            if (otherParam != null && !otherParam.isEmpty()) {
                if (otherParam.containsKey("overtimeLogMap")) {
                    overtimeLogMap = (Map<String, Object>) otherParam.get("overtimeLogMap");
                }
            }
            String changeType_key = date+"_"+changeType;
            KQOvertimeRulesDetailEntity kqOvertimeRulesDetailEntity = overRulesDetailMap.get(changeType_key);
            if(kqOvertimeRulesDetailEntity == null){
                logOvertimeMap(overtimeLogMap,changeType_key,"|加班规则为null|KQOvertimeRulesDetailEntity");
                logger.info("加班转调休记录日志保存失败。获取不到加班规则信息=" + JSONObject.toJSONString(changeType_key));
                return "-1";
            }else{
                logger.info("加班转调休记录。kqOvertimeRulesDetailEntity=" + JSONObject.toJSONString(kqOvertimeRulesDetailEntity));
            }
            String timepoint_key = Util.null2String(otherParam.get("timepoint_key"));
            /**
             * 判断是否允许加班补偿，若未允许，则不累加调休时长，只记录记录使用记录
             * paidLeaveEnable：是否允许加班补偿：0-不允许、1-允许
             */
            String overtime_type = "";
            int paidLeaveEnable = kqOvertimeRulesDetailEntity.getPaidLeaveEnable();
            int paidLeaveEnableType = kqOvertimeRulesDetailEntity.getPaidLeaveEnableType();
            if(2 == paidLeaveEnableType){
                if(otherParam != null && !otherParam.isEmpty()){
                    if(otherParam.containsKey("overtime_type")){
                        overtime_type = Util.null2String(otherParam.get("overtime_type"));
                        if("0".equalsIgnoreCase(overtime_type)){
                            paidLeaveEnable = 1;
                        }else if("1".equalsIgnoreCase(overtime_type)){
                            paidLeaveEnable = 0;
                        }else{
                            paidLeaveEnable = -1;
                        }
                    }
                }
            }
            logOvertimeMap(overtimeLogMap,params,timepoint_key+"|生成调休参数|params");
            logOvertimeMap(overtimeLogMap,paidLeaveEnable,timepoint_key+"|是否生成调休|paidLeaveEnable");

            usageHistoryEntity = new KQUsageHistoryEntity(leaveRulesId,resourceId,requestId,resourceId,currentDate,currentTime,fromCardOrFlow.equals("1") ? "3" : "4",belongYear,"0","0","0","0","0"
                    ,"0","0","0","0","0","0","0",""+minimumUnit,""+minimumUnit,"","-1",workflowid);

            if (paidLeaveEnable != 1) {
                logger.info("未开启加班转调休，记录使用日志。params=" + JSONObject.toJSONString(params));
                /*记录使用记录*/
                usageHistoryEntity.setInsertOrUpdate("PaidLeaveEnable");
                usageHistoryEntityList.add(usageHistoryEntity);
                flag = usageHistoryBiz.save(usageHistoryEntityList);
                if (!flag){
                    logger.info("加班转调休记录日志保存失败。params=" + JSONObject.toJSONString(params));
                }
                return "-1";
            }else {
                String paidLeaveEnableInfo = "paidLeaveEnable:"+paidLeaveEnable+":paidLeaveEnableType:"+paidLeaveEnableType+":overtime_type:"+overtime_type;
                logOvertimeMap(overtimeLogMap,paidLeaveEnableInfo,"|未开启调休|paidLeaveEnable");
            }

            if(1 == paidLeaveEnableType){
//              默认加班补偿规则
//              1-按加班时长比例转调休时长、2-按加班时长范围设置转调休时长、3-按加班的时间段设置转调休时长
                int paidLeaveEnableDefaultType = kqOvertimeRulesDetailEntity.getPaidLeaveEnableDefaultType();
                if(1 == paidLeaveEnableDefaultType){
                    Map<String,String> resultMap = save_paidLeaveEnableDefaultType1(kqOvertimeRulesDetailEntity,_durationOfOvertime,date,leaveRulesId, resourceId,belongYear,expirationDate,fromCardOrFlow);
                    if(!resultMap.isEmpty()){
                        return handleUsageHistory(resultMap,params,usageHistoryEntity);
                    }
                }else if(2 == paidLeaveEnableDefaultType){
                    int ruleDetailid = kqOvertimeRulesDetailEntity.getId();
                    if(ruleDetailid > 0){
                        Map<String,String> resultMap = save_paidLeaveEnableDefaultType2(date,leaveRulesId, resourceId,belongYear,expirationDate,fromCardOrFlow,ruleDetailid,min_durationOfOvertime,minimumUnit,_hoursToDay);
                        if(!resultMap.isEmpty()){
                            return handleUsageHistory(resultMap,params,usageHistoryEntity);
                        }
                    }
                }else if(3 == paidLeaveEnableDefaultType){
                    int ruleDetailid = kqOvertimeRulesDetailEntity.getId();
                    if(ruleDetailid > 0) {
                        if (otherParam != null && !otherParam.isEmpty()) {
                            if (otherParam.containsKey("OvertimeBalanceTimeBean")) {
                                OvertimeBalanceTimeBean overtimeBalanceTimeBean = (OvertimeBalanceTimeBean) otherParam.get("OvertimeBalanceTimeBean");
                                if(overtimeBalanceTimeBean != null){
                                    int list_index = overtimeBalanceTimeBean.getList_index();
                                    if(list_index > -1) {
                                        int timepoint_min = overtimeBalanceTimeBean.getTimepoint_mins();
                                        if(timepoint_min <= 0){
                                            logger.info("时间区间内加班生成为0。overtimeBalanceTimeBean=" + JSONObject.toJSONString(overtimeBalanceTimeBean));
                                        }
                                        Map<String, String> resultMap = save_paidLeaveEnableDefaultType3(date,
                                                leaveRulesId, resourceId, belongYear, expirationDate, fromCardOrFlow,
                                                ruleDetailid, overtimeBalanceTimeBean,list_index,minimumUnit,_hoursToDay,overtimeLogMap,timepoint_key);
                                        if(!resultMap.isEmpty()){
                                            return handleUsageHistory(resultMap,params,usageHistoryEntity);
                                        }
                                    }else{
                                        logger.info("时间区间内加班下标为-1，overtimeBalanceTimeBean=" + JSONObject.toJSONString(overtimeBalanceTimeBean));
                                    }
                                }
                            }
                        }
                    }
                }
            }else if(2 == paidLeaveEnableType){
                //根据员工在加班流程上选择的加班补偿类型进行补偿两种
//              1-按加班时长比例转调休时长、2-按加班时长范围设置转调休时长、3-按加班的时间段设置转调休时长
                int paidLeaveEnableFlowType = kqOvertimeRulesDetailEntity.getPaidLeaveEnableFlowType();
                if(1 == paidLeaveEnableFlowType){
                    Map<String,String> resultMap = save_paidLeaveEnableDefaultType1(kqOvertimeRulesDetailEntity,_durationOfOvertime,date,leaveRulesId, resourceId,belongYear,expirationDate,fromCardOrFlow);
                    if(!resultMap.isEmpty()){
                        return handleUsageHistory(resultMap,params,usageHistoryEntity);
                    }
                }else if(2 == paidLeaveEnableFlowType){
                    int ruleDetailid = kqOvertimeRulesDetailEntity.getId();
                    if(ruleDetailid > 0){
                        Map<String,String> resultMap = save_paidLeaveEnableDefaultType2(date,leaveRulesId, resourceId,belongYear,expirationDate,fromCardOrFlow,ruleDetailid,min_durationOfOvertime,
                                minimumUnit, _hoursToDay);
                        if(!resultMap.isEmpty()){
                            return handleUsageHistory(resultMap,params,usageHistoryEntity);
                        }
                    }
                }else if(3 == paidLeaveEnableFlowType){
                    int ruleDetailid = kqOvertimeRulesDetailEntity.getId();
                    if(ruleDetailid > 0) {
                        if (otherParam != null && !otherParam.isEmpty()) {
                            if (otherParam.containsKey("OvertimeBalanceTimeBean")) {
                                OvertimeBalanceTimeBean overtimeBalanceTimeBean = (OvertimeBalanceTimeBean) otherParam.get("OvertimeBalanceTimeBean");
                                if(overtimeBalanceTimeBean != null){
                                    int list_index = overtimeBalanceTimeBean.getList_index();
                                    if(list_index > -1) {
                                        int timepoint_min = overtimeBalanceTimeBean.getTimepoint_mins();
                                        if(timepoint_min <= 0){
                                            logger.info("时间区间内加班生成为0。overtimeBalanceTimeBean=" + JSONObject.toJSONString(overtimeBalanceTimeBean));
                                        }
                                        Map<String, String> resultMap = save_paidLeaveEnableDefaultType3(date,
                                                leaveRulesId, resourceId, belongYear, expirationDate, fromCardOrFlow,
                                                ruleDetailid, overtimeBalanceTimeBean,list_index, minimumUnit,
                                                _hoursToDay, overtimeLogMap, timepoint_key);
                                        if(!resultMap.isEmpty()){
                                            return handleUsageHistory(resultMap,params,usageHistoryEntity);
                                        }
                                    }else{
                                        logger.info("时间区间内加班下标为-1，overtimeBalanceTimeBean=" + JSONObject.toJSONString(overtimeBalanceTimeBean));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.info("加班转调休出错。params=" + JSONObject.toJSONString(params));
            new BaseBean().writeLog(e);
        }
        return tiaoxiuId;
    }


    /**
     * 输出日志并记录假期使用记录
     * @param resultMap
     * @param params
     * @param usageHistoryEntity
     * @return
     */
    private static String handleUsageHistory(Map<String, String> resultMap, Map<String, Object> params, KQUsageHistoryEntity usageHistoryEntity) {

        boolean flag = false;
        KQUsageHistoryBiz usageHistoryBiz = new KQUsageHistoryBiz();
        List<KQUsageHistoryEntity> usageHistoryEntityList = new ArrayList<KQUsageHistoryEntity>();

        String result_status = Util.null2String(resultMap.get("status"));
        String extraAmount = Util.null2s(Util.null2String(resultMap.get("extraAmount")),"0");
        String tiaoxiuId = Util.null2String(resultMap.get("tiaoxiuId"));
        if("-1".equalsIgnoreCase(result_status)){
            logger.info("加班转调休余额为0，不产生调休明细，记录使用日志。params=" + JSONObject.toJSONString(params));
            usageHistoryEntity.setInsertOrUpdate("OnlyRecordLog");
            usageHistoryEntity.setNewExtraAmount("0");
      /*记录使用记录*/
            usageHistoryEntityList.add(usageHistoryEntity);
            flag = usageHistoryBiz.save(usageHistoryEntityList);
            if (!flag) {
                logger.info("加班转调休记录日志保存失败。params=" + JSONObject.toJSONString(params));
            }
            return "-1";
        }
        if("-2".equalsIgnoreCase(result_status)){
            logger.info("加班转调休保存失败。params=" + JSONObject.toJSONString(params));
            return "-1";
        }
        if("1".equalsIgnoreCase(result_status)){
            usageHistoryEntity.setInsertOrUpdate("insert");
            usageHistoryEntity.setNewExtraAmount(extraAmount);
            usageHistoryEntity.setBalanceOfLeaveId(tiaoxiuId);
            usageHistoryEntityList.add(usageHistoryEntity);
            flag = usageHistoryBiz.save(usageHistoryEntityList);
            if (!flag) {
                logger.info("加班转调休记录日志保存失败。params=" + JSONObject.toJSONString(params));
            }
        }
        return tiaoxiuId;
    }

    /**
     * 按加班的时间段设置转调休时长
     * @param date
     * @param leaveRulesId
     * @param resourceId
     * @param belongYear
     * @param expirationDate
     * @param fromCardOrFlow
     * @param ruleDetailid
     * @param overtimeBalanceTimeBean
     * @param list_index
     * @param minimumUnit
     * @param _hoursToDay
     * @param overtimeLogMap
     * @param timepoint_key
     * @return
     */
    private static Map<String, String> save_paidLeaveEnableDefaultType3(String date,
                                                                        String leaveRulesId, String resourceId, String belongYear, String expirationDate,
                                                                        String fromCardOrFlow, int ruleDetailid, OvertimeBalanceTimeBean overtimeBalanceTimeBean,
                                                                        int list_index, int minimumUnit, BigDecimal _hoursToDay,
                                                                        Map<String, Object> overtimeLogMap, String timepoint_key) {
        Map<String,String> resultMap = Maps.newHashMap();

        String tiaoxiuId = "-1";
        boolean flag = false;
        RecordSet recordSet = new RecordSet();
        String extraAmount = "";

        KQOvertimeRulesBiz kqOvertimeRulesBiz = new KQOvertimeRulesBiz();
        Map<String, List<String>> balanceLengthDetailMap = kqOvertimeRulesBiz.getBalanceTimeDetailMap(ruleDetailid);

        int timepoint_min = overtimeBalanceTimeBean.getTimepoint_mins();
        List<String> lenOfOvertimeList = balanceLengthDetailMap.get("lenOfOvertimeList");
        List<String> lenOfLeaveList = balanceLengthDetailMap.get("lenOfLeaveList");
        String lenOfOvertime = lenOfOvertimeList.get(list_index);
        String lenOfLeave = lenOfLeaveList.get(list_index);
        int changeType = KQOvertimeRulesBiz.getChangeType(resourceId, date);//调休类型


        BigDecimal _timepoint_min = new BigDecimal(Util.null2s(""+timepoint_min, "0"));
        BigDecimal _lenOfLeave = new BigDecimal(lenOfLeave);
        BigDecimal _lenOfOvertime = new BigDecimal(lenOfOvertime);
        BigDecimal durationOfLeave = _timepoint_min.multiply(_lenOfLeave).divide(_lenOfOvertime, 5, RoundingMode.HALF_UP);

        if (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4) {//调休单位为天
            durationOfLeave = durationOfLeave.divide(new BigDecimal("60").multiply(_hoursToDay), 5, RoundingMode.HALF_UP);

        } else if (KQUnitBiz.isLeaveHour(minimumUnit+"")) {//调休单位为小时
            durationOfLeave = durationOfLeave.divide(new BigDecimal("60"), 5, RoundingMode.HALF_UP);
        }

        extraAmount = durationOfLeave.setScale(5, RoundingMode.HALF_UP).toPlainString();
        String paidLeaveEnableDefaultType3 = "minimumUnit:"+minimumUnit+":durationOfLeave:"+durationOfLeave
                +":_lenOfLeave:"+_lenOfLeave+":_lenOfOvertime:"+_lenOfOvertime+":_timepoint_min:"+_timepoint_min+":_hoursToDay:"+_hoursToDay;
        logOvertimeMap(overtimeLogMap,paidLeaveEnableDefaultType3,timepoint_key+"|生成调休|save_paidLeaveEnableDefaultType3");

        if (Util.getDoubleValue(extraAmount) <= 0) {
            resultMap.put("status", "-1");
            return resultMap;
        }
    /*插入调休时长*/
        String _belongYear = date.substring(0, 4);
        String _belongMonth = date.substring(5, 7);
        String sql = "insert into kq_balanceOfLeave(leaveRulesId,resourceId,belongYear,belongMonth,baseAmount,tiaoxiuamount,usedAmount,baseAmount2,extraAmount2,usedAmount2,status,expirationDate,effectiveDate,overtimeType,changeType,isDelete) " +
                "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        flag = recordSet.executeUpdate(sql, leaveRulesId, resourceId, _belongYear, _belongMonth, 0, extraAmount, 0, 0, 0, 0, 0, expirationDate, date, fromCardOrFlow.equals("0") ? "3" : "4",changeType, 0);
        if (!flag) {
            resultMap.put("status", "-2");
            return resultMap;
        }
        //获取刚才插入的调休明细的ID
        sql = "select max(id) from kq_balanceOfLeave where leaveRulesId=? and resourceId=? and belongYear=?";
        recordSet.executeQuery(sql, leaveRulesId, resourceId, belongYear);
        if (recordSet.next()) {
            tiaoxiuId = recordSet.getString(1);
        }
        resultMap.put("status", "1");
        resultMap.put("tiaoxiuId", tiaoxiuId);
        resultMap.put("extraAmount", extraAmount);
        return resultMap;
    }

    /**
     * 按加班时长范围设置转调休时长
     * @param date
     * @param leaveRulesId
     * @param resourceId
     * @param belongYear
     * @param expirationDate
     * @param fromCardOrFlow
     * @param ruleDetailid
     * @param min_durationOfOvertime
     * @param minimumUnit
     * @param _hoursToDay
     * @return
     */
    private static Map<String, String> save_paidLeaveEnableDefaultType2(
            String date, String leaveRulesId, String resourceId,
            String belongYear, String expirationDate, String fromCardOrFlow, int ruleDetailid,
            BigDecimal min_durationOfOvertime, int minimumUnit, BigDecimal _hoursToDay) {
        Map<String,String> resultMap = Maps.newHashMap();

        String tiaoxiuId = "-1";
        boolean flag = false;
        RecordSet recordSet = new RecordSet();
        String extraAmount = "";
        int changeType = KQOvertimeRulesBiz.getChangeType(resourceId, date);//调休类型

        int overtimelength_i = -1;
        KQOvertimeRulesBiz kqOvertimeRulesBiz = new KQOvertimeRulesBiz();
        Map<String,List<String>> balanceLengthDetailMap = kqOvertimeRulesBiz.getBalanceLengthDetailMap(ruleDetailid);
        if(balanceLengthDetailMap != null && !balanceLengthDetailMap.isEmpty()){
            List<String> overtimelengthList = balanceLengthDetailMap.get("overtimelengthList");
            for(int i = 0 ; i < overtimelengthList.size() ; i++){
                String overtimelength = Util.null2s(overtimelengthList.get(i),"");
                if(overtimelength.length() == 0){
                    continue;
                }
                BigDecimal b_60Mins = new BigDecimal(60);
                BigDecimal b_overtimelength = new BigDecimal(overtimelength).multiply(b_60Mins);
                if(min_durationOfOvertime.compareTo(b_overtimelength) >= 0){
                    overtimelength_i = i;
                    break;
                }
            }
            if(overtimelength_i  > -1){
                List<String> balancelengthList = balanceLengthDetailMap.get("balancelengthList");
                if(balancelengthList.size() > overtimelength_i){
                    String balancelength = balancelengthList.get(overtimelength_i);
                    BigDecimal b_balancelength = new BigDecimal(balancelength);
                    if (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4) {//调休单位为天
                        b_balancelength = b_balancelength.divide(_hoursToDay, 5, RoundingMode.HALF_UP);

                    } else if (KQUnitBiz.isLeaveHour(minimumUnit+"")) {//调休单位为小时
                    }
                    extraAmount = Util.null2s(b_balancelength.toPlainString(), "0");
                }
            }
        }
        new KQLog().info("save_paidLeaveEnableDefaultType2 balanceLengthDetailMap:"+balanceLengthDetailMap
                +":min_durationOfOvertime:"+min_durationOfOvertime+":extraAmount:"+extraAmount);

        if (Util.getDoubleValue(extraAmount) <= 0) {
            extraAmount = "0.0";
        }
        if(overtimelength_i < 0){
            extraAmount = "0.0";
        }
    /*插入调休时长*/
        String _belongYear = date.substring(0, 4);
        String _belongMonth = date.substring(5, 7);
        String sql = "insert into kq_balanceOfLeave(leaveRulesId,resourceId,belongYear,belongMonth,baseAmount,tiaoxiuamount,usedAmount,baseAmount2,extraAmount2,usedAmount2,status,expirationDate,effectiveDate,overtimeType,changeType,isDelete) " +
                "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        flag = recordSet.executeUpdate(sql, leaveRulesId, resourceId, _belongYear, _belongMonth, 0, extraAmount, 0, 0, 0, 0, 0, expirationDate, date, fromCardOrFlow.equals("0") ? "3" : "4", changeType,0);
        if (!flag) {
            resultMap.put("status", "-2");
            return resultMap;
        }
        //获取刚才插入的调休明细的ID
        sql = "select max(id) from kq_balanceOfLeave where leaveRulesId=? and resourceId=? and belongYear=?";
        recordSet.executeQuery(sql, leaveRulesId, resourceId, belongYear);
        if (recordSet.next()) {
            tiaoxiuId = recordSet.getString(1);
        }
        resultMap.put("status", "1");
        resultMap.put("tiaoxiuId", tiaoxiuId);
        resultMap.put("extraAmount", extraAmount);
        return resultMap;
    }

    /**
     * 按加班时长比例转调休时长
     * @param kqOvertimeRulesDetailEntity
     * @param _durationOfOvertime
     * @param date
     * @param leaveRulesId
     * @param resourceId
     * @param belongYear
     * @param expirationDate
     * @param fromCardOrFlow
     * @return
     */
    private static Map<String,String> save_paidLeaveEnableDefaultType1(
            KQOvertimeRulesDetailEntity kqOvertimeRulesDetailEntity,
            BigDecimal _durationOfOvertime, String date, String leaveRulesId, String resourceId,
            String belongYear, String expirationDate, String fromCardOrFlow) {
        Map<String,String> resultMap = Maps.newHashMap();

        String tiaoxiuId = "-1";
        boolean flag = false;
        RecordSet recordSet = new RecordSet();
        String extraAmount = "";
        int changeType = KQOvertimeRulesBiz.getChangeType(resourceId, date);//调休类型
        /**
         * 计算调休时长并存入调休余额中
         */
        BigDecimal _lenOfLeave = new BigDecimal("" + kqOvertimeRulesDetailEntity.getLenOfLeave());
        BigDecimal _lenOfOvertime = new BigDecimal("" + kqOvertimeRulesDetailEntity.getLenOfOvertime());
        BigDecimal durationOfLeave = _durationOfOvertime.multiply(_lenOfLeave).divide(_lenOfOvertime, 5, RoundingMode.HALF_UP);
        extraAmount = durationOfLeave.setScale(5, RoundingMode.HALF_UP).toPlainString();

        if (durationOfLeave.doubleValue() <= 0) {
            extraAmount = "0.0";
        }
    /*插入调休时长*/
        String _belongYear = date.substring(0, 4);
        String _belongMonth = date.substring(5, 7);
        new BaseBean().writeLog("==zj==(按加班时长比例转调休时长)" + JSON.toJSONString(changeType));
        String sql = "insert into kq_balanceOfLeave(leaveRulesId,resourceId,belongYear,belongMonth,baseAmount,tiaoxiuamount,usedAmount,baseAmount2,extraAmount2,usedAmount2,status,expirationDate,effectiveDate,overtimeType,changeType,isDelete) " +
                "values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        flag = recordSet.executeUpdate(sql, leaveRulesId, resourceId, _belongYear, _belongMonth, 0, extraAmount, 0, 0, 0, 0, 0, expirationDate, date, fromCardOrFlow.equals("0") ? "3" : "4",changeType, 0);
        if (!flag) {
            resultMap.put("status", "-2");
            return resultMap;
        }
        //获取刚才插入的调休明细的ID
        sql = "select max(id) from kq_balanceOfLeave where leaveRulesId=? and resourceId=? and belongYear=?";
        recordSet.executeQuery(sql, leaveRulesId, resourceId, belongYear);
        if (recordSet.next()) {
            tiaoxiuId = recordSet.getString(1);
        }
        resultMap.put("status", "1");
        resultMap.put("tiaoxiuId", tiaoxiuId);
        resultMap.put("extraAmount", extraAmount);
        return resultMap;
    }

    /**
     * 根据员工假期余额的ID
     *
     * @param balanceOfLeaveId   调休余额的ID
     * @param durationOfOvertime 加班时长，默认为分钟
     * @return
     */
    public static boolean updateExtraAmountByDis5(String balanceOfLeaveId, double durationOfOvertime, String requestId) {
        boolean result = false;
        try {
            if (balanceOfLeaveId == null && !balanceOfLeaveId.equals("")) {
                logger.info("传入的员工假期余额ID为空。");
                logger.info("balanceOfLeaveId=" + balanceOfLeaveId + ",newExtraAmount=" + durationOfOvertime + ",requestId=" + requestId);
                return false;
            }
            String workflowid = "-1";
            RecordSet rs = new RecordSet();
            if(requestId.length() > 0 && Util.getIntValue(requestId) > 0){
                String workflow_sql = "select workflowid from workflow_requestbase where requestid = ? ";
                rs.executeQuery(workflow_sql, requestId);
                if(rs.next()){
                    workflowid = Util.null2s(rs.getString("workflowid"),"-1");
                }
            }

            //获取当前日期，当前时间
            Calendar today = Calendar.getInstance();
            //当前日期
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            //当前时间
            String currentTime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                    Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                    Util.add0(today.get(Calendar.SECOND), 2);

            //假期类型的缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //[调休]的假期类型的ID
            String leaveRulesId = "";
            //找到[调休]的假期类型ID
            rulesComInfo.setTofirstRow();
            while (rulesComInfo.next()) {
                if (KQLeaveRulesBiz.isTiaoXiu(rulesComInfo.getId())) {
                    leaveRulesId = rulesComInfo.getId();
                    break;
                }
            }
            //[调休]的假期类型的最小请假单位：1-按天、2-按半天、3-按小时、4-按整天
            int minimumUnit = Util.getIntValue(rulesComInfo.getMinimumUnit(leaveRulesId), 3);

            /****************************************************************************************************************/

            //加班单位中的日折算时长
            double hoursToDay = 8.00;
            String overtimeSql = "select * from kq_OvertimeUnit where (isDelete is null or isDelete !=1) and id=1";
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(overtimeSql);
            if (recordSet.next()) {
                hoursToDay = Util.getDoubleValue(recordSet.getString("hoursToDay"), 8.00);
            }

            /****************************************************************************************************************/

            BigDecimal _hoursToDay = new BigDecimal("" + hoursToDay);
            BigDecimal _durationOfOvertime = new BigDecimal("" + durationOfOvertime);
            if (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4) {//调休单位为天
                //加班时长类型为分钟、调休单位为天----将加班时长转换为天
                _durationOfOvertime = _durationOfOvertime.divide(new BigDecimal("60").multiply(_hoursToDay), 5, RoundingMode.HALF_UP);
            } else if (KQUnitBiz.isLeaveHour(minimumUnit+"")) {//调休单位为小时
                //加班时长类型为分钟、调休单位为小时----将加班时长转换为小时
                _durationOfOvertime = _durationOfOvertime.divide(new BigDecimal("60"), 5, RoundingMode.HALF_UP);
            }
            logger.info("记录一下需要重新生成调休时长的调休时长。");
            logger.info("balanceOfLeaveId=" + balanceOfLeaveId + ",newExtraAmount=" + durationOfOvertime + ",requestId=" + requestId + ",_durationOfOvertime=" + _durationOfOvertime.doubleValue());

            boolean flag = true;
            KQUsageHistoryBiz kqUsageHistoryBiz = new KQUsageHistoryBiz();
            KQUsageHistoryEntity kqUsageHistoryEntity = new KQUsageHistoryEntity();
            List<KQUsageHistoryEntity> kqUsageHistoryEntities = new ArrayList<>();

            String sql = "select * from KQ_BalanceOfLeave where id=?";
            recordSet.executeQuery(sql, balanceOfLeaveId);
            if (recordSet.next()) {
                String id = recordSet.getString("id");
                String resourceId = recordSet.getString("resourceId");
                String belongYear = recordSet.getString("belongYear");
                String belongMonth = recordSet.getString("belongMonth");
                String baseAmount = recordSet.getString("baseAmount");
                String extraAmount = recordSet.getString("extraAmount");
                String usedAmount = recordSet.getString("usedAmount");
                String effectiveDate = recordSet.getString("effectiveDate");
                String expirationDate = recordSet.getString("expirationDate");

                //根据加班时长以及转换比例得出调休时长
                BigDecimal _lenOfLeave = new BigDecimal("" + KQOvertimeRulesBiz.getLenOfLeave(resourceId, effectiveDate));
                BigDecimal _lenOfOvertime = new BigDecimal("" + KQOvertimeRulesBiz.getLenOfOvertime(resourceId, effectiveDate));
                BigDecimal durationOfLeave = _durationOfOvertime.multiply(_lenOfLeave).divide(_lenOfOvertime, 5, RoundingMode.HALF_UP);

                sql = "update KQ_BalanceOfLeave set extraAmount=? where id=?";
                result = recordSet.executeUpdate(sql, durationOfLeave.setScale(5, RoundingMode.HALF_UP).doubleValue(), id);
                if (result) {
                    kqUsageHistoryEntities = new ArrayList<>();
                    kqUsageHistoryEntity = new KQUsageHistoryEntity(leaveRulesId, resourceId, requestId, resourceId, currentDate, currentTime, "8", belongYear, baseAmount, baseAmount, extraAmount, durationOfLeave.setScale(5, RoundingMode.HALF_UP).toPlainString(), usedAmount
                            , usedAmount, "0", "0", "0", "0", "0", "0", "" + minimumUnit, "" + minimumUnit, "update", id,workflowid);
                    kqUsageHistoryEntities.add(kqUsageHistoryEntity);
                    kqUsageHistoryBiz.save(kqUsageHistoryEntities);
                } else {
                    logger.info("加班重新生成调休，员工假期余额更新失败。");
                    logger.info("balanceOfLeaveId=" + balanceOfLeaveId + ",newExtraAmount=" + durationOfOvertime + ",requestId=" + requestId + ",_durationOfOvertime=" + durationOfLeave.doubleValue());
                    return false;
                }
            } else {
                logger.info("未找到对应的员工假期余额。");
                logger.info("balanceOfLeaveId=" + balanceOfLeaveId + ",newExtraAmount=" + durationOfOvertime + ",requestId=" + requestId);
                return false;
            }
        } catch (Exception e) {
            new BaseBean().writeLog(e);
        }
        return result;
    }

    /**
     * 累加已用的假期时长
     *
     * @param resourceId 指定人员ID
     * @param date       请假日期(目前请假扣减逻辑：如果请假跨了多天，会拆分成一天一天扣减)
     * @param ruleId     指定假期规则
     * @param duration   请假时长
     * @param type       时长类型：0-分钟、1-小时、2-天
     *                   传入空值则默认取假期规则设置中的单位
     * @param fromDateDb 请假流程表单中的请假开始日期(根据请假开始日期判断假期余额是否有效)
     * @param requestId  请假流程的requestId
     */
    public static void addUsedAmount(String resourceId, String date, String ruleId, String duration, String type, String requestId, String fromDateDb) {
        try {
            /*获取当前日期，当前时间*/
            Calendar today = Calendar.getInstance();
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            String currentTime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                    Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                    Util.add0(today.get(Calendar.SECOND), 2);

            /****************************************************************************************************************/

            //人力资源缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subcompanyId = resourceComInfo.getSubCompanyID(resourceId);

            /****************************************************************************************************************/

            //假期类型缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假
            int minimumUnit = Util.getIntValue(rulesComInfo.getMinimumUnit(ruleId));
            //最小请假单位获取有误，记录错误日志，直接退出方法
            if (minimumUnit < 1 || minimumUnit > 6) {
                logger.info("最小请假单位获取有误。resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }
            //是否启用假期余额：0-不启用、1-启用
            int balanceEnable = Util.getIntValue(rulesComInfo.getBalanceEnable(ruleId), 0);
            //该假期未开启假期余额，记录错误日志，退出方法
            if (balanceEnable != 1) {
                logger.info("该假期未开启假期余额。resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }
            //前台传回的请假时长类型与后台设置的假期限制的最小请假单位不相符,暂不支持转换，记录错误日志，直接退出方法
            boolean flag = type.equals("") || ("1".equals(type) && KQUnitBiz.isLeaveHour(minimumUnit+"")) || ("2".equals(type) && (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4));
            if (!flag) {
                logger.info("前台传回的请假时长类型与后台设置的请假类型限制的最小请假单位不相符。" +
                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }

            /****************************************************************************************************************/

            //假期规则缓存类(每个假期类型下可能存在多个假期规则)
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //余额发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subcompanyId));
            //扣减优先级：1-法定年假、2-福利年假
            int priority = Util.getIntValue(detailComInfo.getPriority(ruleId, subcompanyId), 1);

            /****************************************************************************************************************/

            //如果请假开始日期为空，默认取请假日期
            fromDateDb = date;

            //员工假期余额变更记录
            KQUsageHistoryEntity usageHistoryEntity = new KQUsageHistoryEntity();
            List<KQUsageHistoryEntity> usageHistoryEntityList = new ArrayList<KQUsageHistoryEntity>();

            List<String> updateList = new ArrayList<String>();
            BigDecimal _duration = new BigDecimal(Util.null2s(duration, "0"));
            RecordSet recordSet = new RecordSet();
            String sql = "select * from kq_balanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and resourceId=" + resourceId + " order by belongYear";

            //qc2474646 扣减为申请日期所在月份的假期余额
            String belongMonth = date.substring(5,7);     //划出月份
            if (distributionMode == 5) {
                String quarter = getCurrentQuarter(Integer.parseInt(belongMonth));
                if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                        || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                    sql = " select * from kq_balanceOfLeave " +
                            " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate='' or expirationDate>='" + fromDateDb + "') and belongMonth IN " + quarter +
                            " order by belongYear asc,expirationDate asc,id asc ";
                } else {
                    sql = " select * from kq_balanceOfLeave " +
                            " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate>='" + fromDateDb + "') and belongMonth IN " + quarter +
                            " order by belongYear asc,expirationDate asc,id asc ";
                }
            }
            new BaseBean().writeLog("==zj==(假期余额扣减sql)"  + sql);
            recordSet.executeQuery(sql);
            int total = recordSet.getCounts();
            int index = 0;
            while (recordSet.next()) {
                index++;
                //
                String id = recordSet.getString("id");
                //所属年份
                String belongYear = recordSet.getString("belongYear");
                //失效日期
                String expirationDate = recordSet.getString("expirationDate");
                //生效日期
                String effectiveDate = recordSet.getString("effectiveDate");
                //判断假期余额是否失效
                boolean status = getBalanceStatus(ruleId, resourceId, belongYear, fromDateDb, effectiveDate, expirationDate);
                if (!status) {
                    continue;
                }
                //基数
                BigDecimal baseAmount = new BigDecimal(Util.null2s(recordSet.getString("baseAmount"), "0"));
                //额外
                BigDecimal extraAmount = new BigDecimal(Util.null2s(recordSet.getString("extraAmount"), "0"));

                //加班生成调休
                BigDecimal tiaoxiuAmount = new BigDecimal(Util.null2s(recordSet.getString("tiaoxiuamount"), "0"));
                //已休
                BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));
                //福利年假的基数
                BigDecimal baseAmount2 = new BigDecimal(Util.null2s(recordSet.getString("baseAmount2"), "0"));
                //福利年假的额外
                BigDecimal usedAmount2 = new BigDecimal(Util.null2s(recordSet.getString("usedAmount2"), "0"));
                //福利年假的已休
                BigDecimal extraAmount2 = new BigDecimal(Util.null2s(recordSet.getString("extraAmount2"), "0"));
                if (distributionMode == 6) {
                    if (baseAmount.add(extraAmount).add(tiaoxiuAmount).subtract(usedAmount).add(baseAmount2).add(extraAmount2).subtract(usedAmount2).doubleValue() <= 0) {
                        continue;
                    }

                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "legal", fromDateDb);
                    baseAmount2 = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount2, "welfare", fromDateDb);
                } else {
                    if (baseAmount.add(extraAmount).add(tiaoxiuAmount).subtract(usedAmount).doubleValue() <= 0) {
                        continue;
                    }
                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "", fromDateDb);
                }

                //员工假期余额使用记录
                usageHistoryEntity = new KQUsageHistoryEntity();
                usageHistoryEntity.setLeaveRulesId(ruleId);
                usageHistoryEntity.setRelatedId(resourceId);
                usageHistoryEntity.setWfRequestId(requestId);
                usageHistoryEntity.setOperator(resourceId);
                usageHistoryEntity.setOperateDate(currentDate);
                usageHistoryEntity.setOperateTime(currentTime);
                usageHistoryEntity.setOperateType("1");
                usageHistoryEntity.setInsertOrUpdate("update");
                usageHistoryEntity.setBelongYear(belongYear);
                usageHistoryEntity.setOldUsedAmount(usedAmount.setScale(5, RoundingMode.HALF_UP).toPlainString());
                usageHistoryEntity.setOldUsedAmount2(usedAmount2.setScale(5, RoundingMode.HALF_UP).toPlainString());
                usageHistoryEntity.setOldMinimumUnit("" + minimumUnit);
                usageHistoryEntity.setNewMinimumUnit("" + minimumUnit);
                usageHistoryEntityList.add(usageHistoryEntity);

                if (distributionMode == 6) {//如果年假为混合模式（法定年假+福利年假）
                    if (priority == 1) {//扣减优先级：先扣减法定年假、再扣减福利年假
                        BigDecimal temp = baseAmount.add(extraAmount).subtract(usedAmount).subtract(_duration);
                        if (temp.doubleValue() >= 0) {
                            String newUsedAmount = usedAmount.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                            String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                            updateList.add(updateSql);

                            usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            break;
                        } else {
                            temp = baseAmount.add(extraAmount).subtract(usedAmount).add(baseAmount2).add(extraAmount2).subtract(usedAmount2).subtract(_duration);
                            //该假期剩余假期余额不足以扣减，记录错误日志，退出方法
                            if (index == total && temp.doubleValue() < 0) {
                                logger.info("该人员的该假期所有的剩余假期余额都不足以扣减。" +
                                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId +
                                        ",baseAmount=" + baseAmount.doubleValue() + ",extraAmount=" + extraAmount.doubleValue() + ",usedAmount=" + usedAmount.doubleValue() +
                                        ",baseAmount2=" + baseAmount2.doubleValue() + ",extraAmount2=" + extraAmount2.doubleValue() + ",usedAmount2=" + usedAmount2.doubleValue());
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5,RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount2 = _duration.subtract(baseAmount.add(extraAmount).subtract(usedAmount)).add(usedAmount2).setScale(5, RoundingMode.HALF_UP).toString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + ",usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            }
                            if (temp.doubleValue() >= 0) {
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount2 = _duration.subtract(baseAmount.add(extraAmount).subtract(usedAmount)).add(usedAmount2).setScale(5, RoundingMode.HALF_UP).toString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + ",usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            } else {
                                _duration = new BigDecimal("0").subtract(temp);
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + ",usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                continue;
                            }
                        }
                    } else {//扣减优先级：先扣减福利年假、再扣减法定年假
                        BigDecimal temp = baseAmount2.add(extraAmount2).subtract(usedAmount2).subtract(_duration);
                        if (temp.doubleValue() >= 0) {
                            String newUsedAmount2 = usedAmount2.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                            String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                            updateList.add(updateSql);

                            usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            break;
                        } else {
                            temp = baseAmount2.add(extraAmount2).subtract(usedAmount2).add(baseAmount).add(extraAmount).subtract(usedAmount).subtract(_duration);
                            /*该假期剩余假期余额不足以扣减，记录错误日志，退出方法*/
                            if (index == total && temp.doubleValue() < 0) {
                                logger.info("该人员的该假期所有的剩余假期余额都不足以扣减。" +
                                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId +
                                        ",baseAmount=" + baseAmount.doubleValue() + ",extraAmount=" + extraAmount.doubleValue() + ",usedAmount=" + usedAmount.doubleValue() +
                                        ",baseAmount2=" + baseAmount2.doubleValue() + ",extraAmount2=" + extraAmount2.doubleValue() + ",usedAmount2=" + usedAmount2.doubleValue());
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount = _duration.subtract(baseAmount2.add(extraAmount2).subtract(usedAmount2)).add(usedAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + ",usedAmount=" + (newUsedAmount) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            }
                            if (temp.doubleValue() >= 0) {
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount = _duration.subtract(baseAmount2.add(extraAmount2).subtract(usedAmount2)).add(usedAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + ",usedAmount=" + (newUsedAmount) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            } else {
                                _duration = new BigDecimal("0").subtract(temp);
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + ",usedAmount=" + (newUsedAmount) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                continue;
                            }
                        }
                    }
                } else {//非混合模式
                    BigDecimal temp = baseAmount.add(extraAmount).add(tiaoxiuAmount).subtract(usedAmount).subtract(_duration);
                    /*该假期剩余假期余额不足以扣减，记录错误日志，退出方法*/
                    if (index == total && temp.doubleValue() < 0) {
                        logger.info("该人员的该假期所有的剩余假期余额都不足以扣减。" +
                                "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId +
                                ",baseAmount=" + baseAmount.doubleValue() + ",extraAmount=" + extraAmount.doubleValue() + ",usedAmount=" + usedAmount.doubleValue());
                        String newUsedAmount = usedAmount.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        break;
                    }
                    if (temp.doubleValue() >= 0) {
                        String newUsedAmount = usedAmount.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        break;
                    } else {
                        _duration = new BigDecimal("0").subtract(temp);
                        String newUsedAmount = baseAmount.add(extraAmount).add(tiaoxiuAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        continue;
                    }
                }
            }

            logger.info("requestId:"+requestId+"::updateList:"+updateList);
            /*SQL操作批处理*/
            for (int i = 0; i < updateList.size(); i++) {
                flag = recordSet.executeUpdate(updateList.get(i));
                if (!flag) {
                    logger.info("员工提交请假流程累计已休假期的SQL执行失败。" +
                            "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                    return;
                }
            }
            /*记录假期使用记录*/
            if (flag && usageHistoryEntityList.size() > 0) {
                KQUsageHistoryBiz usageHistoryBiz = new KQUsageHistoryBiz();
                flag = usageHistoryBiz.save(usageHistoryEntityList);
                if (!flag) {
                    logger.info("请假流程，员工假期余额变更记录SQL执行失败。" +
                            "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                    return;
                }
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.info("请假流程扣减出错。" +
                    "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
            return;
        }
    }

    /**
     * 累加已用的假期时长
     *
     * @param resourceId 指定人员ID
     * @param date       请假日期(目前请假扣减逻辑：如果请假跨了多天，会拆分成一天一天扣减)
     * @param ruleId     指定假期规则
     * @param duration   请假时长
     * @param type       时长类型：0-分钟、1-小时、2-天
     *                   传入空值则默认取假期规则设置中的单位
     * @param fromDateDb 请假流程表单中的请假开始日期(根据请假开始日期判断假期余额是否有效)
     * @param requestId  请假流程的requestId
     */
    public static void addUsedAmount(String resourceId, String date, String ruleId, String duration, String type, String requestId, String fromDateDb,String reduceMonth) {
        try {
            /*获取当前日期，当前时间*/
            Calendar today = Calendar.getInstance();
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            String currentTime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                    Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                    Util.add0(today.get(Calendar.SECOND), 2);

            /****************************************************************************************************************/

            //人力资源缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subcompanyId = resourceComInfo.getSubCompanyID(resourceId);

            /****************************************************************************************************************/

            //假期类型缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假
            int minimumUnit = Util.getIntValue(rulesComInfo.getMinimumUnit(ruleId));
            //最小请假单位获取有误，记录错误日志，直接退出方法
            if (minimumUnit < 1 || minimumUnit > 6) {
                logger.info("最小请假单位获取有误。resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }
            //是否启用假期余额：0-不启用、1-启用
            int balanceEnable = Util.getIntValue(rulesComInfo.getBalanceEnable(ruleId), 0);
            //该假期未开启假期余额，记录错误日志，退出方法
            if (balanceEnable != 1) {
                logger.info("该假期未开启假期余额。resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }
            //前台传回的请假时长类型与后台设置的假期限制的最小请假单位不相符,暂不支持转换，记录错误日志，直接退出方法
            boolean flag = type.equals("") || ("1".equals(type) && KQUnitBiz.isLeaveHour(minimumUnit+"")) || ("2".equals(type) && (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4));
            if (!flag) {
                logger.info("前台传回的请假时长类型与后台设置的请假类型限制的最小请假单位不相符。" +
                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }

            /****************************************************************************************************************/

            //假期规则缓存类(每个假期类型下可能存在多个假期规则)
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //余额发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subcompanyId));
            //扣减优先级：1-法定年假、2-福利年假
            int priority = Util.getIntValue(detailComInfo.getPriority(ruleId, subcompanyId), 1);

            /****************************************************************************************************************/

            //如果请假开始日期为空，默认取请假日期
            fromDateDb = date;

            //员工假期余额变更记录
            KQUsageHistoryEntity usageHistoryEntity = new KQUsageHistoryEntity();
            List<KQUsageHistoryEntity> usageHistoryEntityList = new ArrayList<KQUsageHistoryEntity>();

            List<String> updateList = new ArrayList<String>();
            BigDecimal _duration = new BigDecimal(Util.null2s(duration, "0"));
            RecordSet recordSet = new RecordSet();
            String sql = "select * from kq_balanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and resourceId=" + resourceId + " order by belongYear";

            //==z 扣减为指定月份
            KqCustomUtil kqCustomUtil = new KqCustomUtil();
            //获取指定月份的头尾
            List<String> monthDays = kqCustomUtil.getMonthDays(reduceMonth);
            String monthFirst = "";//月头
            String monthEnd = "";//月尾
            if (monthDays.size() > 0){
                for (int i = 0; i < monthDays.size(); i++) {
                    if (i == 0){
                        monthFirst = monthDays.get(i);
                    }
                    if (i == 1){
                        monthEnd = monthDays.get(i);
                    }
                }
            }
            if (distributionMode == 5) {
                /**
                 * 不用季度范围了，扣减为指定月份
                 */
                /*String quarter = getCurrentQuarter(Integer.parseInt(belongMonth));*/
                if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                        || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                    sql = " select * from kq_balanceOfLeave " +
                            " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate='' or expirationDate>='" + fromDateDb + "') and effectiveDate between '" + monthFirst +"' and '"+monthEnd+"' "+
                            " order by belongYear asc,expirationDate asc,case when changetype=2 then 1 when changetype=3 then 2 else 3 end  ";
                } else {
                    sql = " select * from kq_balanceOfLeave " +
                            " where (isDelete is null or isDelete<>1) and resourceId=" + resourceId + " and leaveRulesId=" + ruleId + " and (expirationDate is null or expirationDate>='" + fromDateDb + "') and effectiveDate between '" + monthFirst +"' and '"+monthEnd+"' "+
                            " order by belongYear asc,expirationDate asc,case when changetype=2 then 1 when changetype=3 then 2 else 3 end  ";
                }
            }
            new BaseBean().writeLog("==zj==(假期余额扣减指定月份扣减sql)"  + sql);
            recordSet.executeQuery(sql);
            int total = recordSet.getCounts();
            int index = 0;
            while (recordSet.next()) {
                index++;
                //
                String id = recordSet.getString("id");
                //所属年份
                String belongYear = recordSet.getString("belongYear");
                //失效日期
                String expirationDate = recordSet.getString("expirationDate");
                //生效日期
                String effectiveDate = recordSet.getString("effectiveDate");
                //判断假期余额是否失效
                boolean status = getBalanceStatus(ruleId, resourceId, belongYear, fromDateDb, effectiveDate, expirationDate);
                if (!status) {
                    continue;
                }
                //基数
                BigDecimal baseAmount = new BigDecimal(Util.null2s(recordSet.getString("baseAmount"), "0"));
                //额外
                BigDecimal extraAmount = new BigDecimal(Util.null2s(recordSet.getString("extraAmount"), "0"));

                //加班生成调休
                BigDecimal tiaoxiuAmount = new BigDecimal(Util.null2s(recordSet.getString("tiaoxiuamount"), "0"));
                //已休
                BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));
                //福利年假的基数
                BigDecimal baseAmount2 = new BigDecimal(Util.null2s(recordSet.getString("baseAmount2"), "0"));
                //福利年假的额外
                BigDecimal usedAmount2 = new BigDecimal(Util.null2s(recordSet.getString("usedAmount2"), "0"));
                //福利年假的已休
                BigDecimal extraAmount2 = new BigDecimal(Util.null2s(recordSet.getString("extraAmount2"), "0"));
                if (distributionMode == 6) {
                    if (baseAmount.add(extraAmount).add(tiaoxiuAmount).subtract(usedAmount).add(baseAmount2).add(extraAmount2).subtract(usedAmount2).doubleValue() <= 0) {
                        continue;
                    }

                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "legal", fromDateDb);
                    baseAmount2 = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount2, "welfare", fromDateDb);
                } else {
                    if (baseAmount.add(extraAmount).add(tiaoxiuAmount).subtract(usedAmount).doubleValue() <= 0) {
                        continue;
                    }
                    baseAmount = getCanUseAmount(resourceId, ruleId, belongYear, baseAmount, "", fromDateDb);
                }

                //员工假期余额使用记录
                usageHistoryEntity = new KQUsageHistoryEntity();
                usageHistoryEntity.setLeaveRulesId(ruleId);
                usageHistoryEntity.setRelatedId(resourceId);
                usageHistoryEntity.setWfRequestId(requestId);
                usageHistoryEntity.setOperator(resourceId);
                usageHistoryEntity.setOperateDate(currentDate);
                usageHistoryEntity.setOperateTime(currentTime);
                usageHistoryEntity.setOperateType("1");
                usageHistoryEntity.setInsertOrUpdate("update");
                usageHistoryEntity.setBelongYear(belongYear);
                usageHistoryEntity.setOldUsedAmount(usedAmount.setScale(5, RoundingMode.HALF_UP).toPlainString());
                usageHistoryEntity.setOldUsedAmount2(usedAmount2.setScale(5, RoundingMode.HALF_UP).toPlainString());
                usageHistoryEntity.setOldMinimumUnit("" + minimumUnit);
                usageHistoryEntity.setNewMinimumUnit("" + minimumUnit);
                usageHistoryEntityList.add(usageHistoryEntity);

                if (distributionMode == 6) {//如果年假为混合模式（法定年假+福利年假）
                    if (priority == 1) {//扣减优先级：先扣减法定年假、再扣减福利年假
                        BigDecimal temp = baseAmount.add(extraAmount).subtract(usedAmount).subtract(_duration);
                        if (temp.doubleValue() >= 0) {
                            String newUsedAmount = usedAmount.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                            String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                            updateList.add(updateSql);

                            usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            break;
                        } else {
                            temp = baseAmount.add(extraAmount).subtract(usedAmount).add(baseAmount2).add(extraAmount2).subtract(usedAmount2).subtract(_duration);
                            //该假期剩余假期余额不足以扣减，记录错误日志，退出方法
                            if (index == total && temp.doubleValue() < 0) {
                                logger.info("该人员的该假期所有的剩余假期余额都不足以扣减。" +
                                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId +
                                        ",baseAmount=" + baseAmount.doubleValue() + ",extraAmount=" + extraAmount.doubleValue() + ",usedAmount=" + usedAmount.doubleValue() +
                                        ",baseAmount2=" + baseAmount2.doubleValue() + ",extraAmount2=" + extraAmount2.doubleValue() + ",usedAmount2=" + usedAmount2.doubleValue());
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5,RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount2 = _duration.subtract(baseAmount.add(extraAmount).subtract(usedAmount)).add(usedAmount2).setScale(5, RoundingMode.HALF_UP).toString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + ",usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            }
                            if (temp.doubleValue() >= 0) {
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount2 = _duration.subtract(baseAmount.add(extraAmount).subtract(usedAmount)).add(usedAmount2).setScale(5, RoundingMode.HALF_UP).toString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + ",usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            } else {
                                _duration = new BigDecimal("0").subtract(temp);
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + ",usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                continue;
                            }
                        }
                    } else {//扣减优先级：先扣减福利年假、再扣减法定年假
                        BigDecimal temp = baseAmount2.add(extraAmount2).subtract(usedAmount2).subtract(_duration);
                        if (temp.doubleValue() >= 0) {
                            String newUsedAmount2 = usedAmount2.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                            String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + " where id=" + id;
                            updateList.add(updateSql);

                            usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            break;
                        } else {
                            temp = baseAmount2.add(extraAmount2).subtract(usedAmount2).add(baseAmount).add(extraAmount).subtract(usedAmount).subtract(_duration);
                            /*该假期剩余假期余额不足以扣减，记录错误日志，退出方法*/
                            if (index == total && temp.doubleValue() < 0) {
                                logger.info("该人员的该假期所有的剩余假期余额都不足以扣减。" +
                                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId +
                                        ",baseAmount=" + baseAmount.doubleValue() + ",extraAmount=" + extraAmount.doubleValue() + ",usedAmount=" + usedAmount.doubleValue() +
                                        ",baseAmount2=" + baseAmount2.doubleValue() + ",extraAmount2=" + extraAmount2.doubleValue() + ",usedAmount2=" + usedAmount2.doubleValue());
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount = _duration.subtract(baseAmount2.add(extraAmount2).subtract(usedAmount2)).add(usedAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + ",usedAmount=" + (newUsedAmount) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            }
                            if (temp.doubleValue() >= 0) {
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount = _duration.subtract(baseAmount2.add(extraAmount2).subtract(usedAmount2)).add(usedAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + ",usedAmount=" + (newUsedAmount) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                break;
                            } else {
                                _duration = new BigDecimal("0").subtract(temp);
                                String newUsedAmount2 = baseAmount2.add(extraAmount2).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String newUsedAmount = baseAmount.add(extraAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=" + (newUsedAmount2) + ",usedAmount=" + (newUsedAmount) + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                continue;
                            }
                        }
                    }
                } else {//非混合模式
                    BigDecimal temp = baseAmount.add(extraAmount).add(tiaoxiuAmount).subtract(usedAmount).subtract(_duration);
                    /*该假期剩余假期余额不足以扣减，记录错误日志，退出方法*/
                    if (index == total && temp.doubleValue() < 0) {
                        logger.info("该人员的该假期所有的剩余假期余额都不足以扣减。" +
                                "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId +
                                ",baseAmount=" + baseAmount.doubleValue() + ",extraAmount=" + extraAmount.doubleValue() + ",usedAmount=" + usedAmount.doubleValue());
                        String newUsedAmount = usedAmount.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        break;
                    }
                    if (temp.doubleValue() >= 0) {
                        String newUsedAmount = usedAmount.add(_duration).setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        break;
                    } else {
                        _duration = new BigDecimal("0").subtract(temp);
                        String newUsedAmount = baseAmount.add(extraAmount).add(tiaoxiuAmount).setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + (newUsedAmount) + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        continue;
                    }
                }
            }

            logger.info("requestId:"+requestId+"::updateList:"+updateList);
            /*SQL操作批处理*/
            for (int i = 0; i < updateList.size(); i++) {
                flag = recordSet.executeUpdate(updateList.get(i));
                if (!flag) {
                    logger.info("员工提交请假流程累计已休假期的SQL执行失败。" +
                            "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                    return;
                }
            }
            /*记录假期使用记录*/
            if (flag && usageHistoryEntityList.size() > 0) {
                KQUsageHistoryBiz usageHistoryBiz = new KQUsageHistoryBiz();
                flag = usageHistoryBiz.save(usageHistoryEntityList);
                if (!flag) {
                    logger.info("请假流程，员工假期余额变更记录SQL执行失败。" +
                            "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                    return;
                }
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.info("请假流程扣减出错。" +
                    "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
            return;
        }
    }

    /**
     * 销假流程归档时，需要将曾经扣减的假期余额返还给相应人员
     *
     * @param resourceId 指定人员ID
     * @param date       指定日期
     *                   根据日期取指定指定日期年份之前的所有有效的余额，传入空值则取所有年份的有效余额
     * @param ruleId     指定假期规则
     * @param duration   请假时长
     * @param type       时长类型：1-小时、2-天
     *                   传入空值则默认取假期规则设置中的单位
     * @param requestId  请假流程的requestId
     * @return
     */
    public static void reduceUsedAmount(String resourceId, String date, String ruleId, String duration, String type, String requestId) {
        try {
            /*获取当前日期，当前时间*/
            Calendar today = Calendar.getInstance();
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
            String currentTime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                    Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                    Util.add0(today.get(Calendar.SECOND), 2);

            /****************************************************************************************************************/

            //人力资源缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部
            String subcompanyId = resourceComInfo.getSubCompanyID(resourceId);

            /****************************************************************************************************************/
            logger.info("reduceUsedAmount:resourceId:"+resourceId+":fromdatedb:"+date+":newLeaveType:"+ruleId+":duration:"+duration+":requestid:"+requestId);

            //假期规则缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假
            int minimumUnit = Util.getIntValue(rulesComInfo.getMinimumUnit(ruleId));
            //最小请假单位获取有误，记录错误日志，直接退出方法
            if (minimumUnit < 1 || minimumUnit > 6) {
                logger.info("最小请假单位获取有误。resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }
            //是否启用假期余额：0-不启用、1-启用
            int balanceEnable = Util.getIntValue(rulesComInfo.getBalanceEnable(ruleId), 0);
            //该假期未开启假期余额，记录错误日志，退出方法
            if (balanceEnable != 1) {
                logger.info("该假期未开启假期余额。resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }
            //前台传回的请假时长类型与后台设置的假期限制的最小请假单位不相符,暂不支持转换，记录错误日志，直接退出方法
            boolean flag = type.equals("") || ("1".equals(type) && KQUnitBiz.isLeaveHour(minimumUnit+"")) || ("2".equals(type) && (minimumUnit == 1 || minimumUnit == 2 || minimumUnit == 4));
            if (!flag) {
                logger.info("前台传回的请假时长类型与后台设置的请假类型限制的最小请假单位不相符。" +
                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                return;
            }

            /****************************************************************************************************************/

            //假期规则缓存类(每个假期类型下可能存在多个假期规则)
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //余额发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subcompanyId));
            //扣减优先级：1-法定年假、2-福利年假
            int priority = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subcompanyId));

            /****************************************************************************************************************/

            logger.info("reduceUsedAmount:distributionMode:"+distributionMode+":priority:"+priority);

            //员工假期余额变更记录
            KQUsageHistoryEntity usageHistoryEntity = new KQUsageHistoryEntity();

            List<KQUsageHistoryEntity> usageHistoryEntityList = new ArrayList<KQUsageHistoryEntity>();

            BigDecimal _duration = new BigDecimal(Util.null2s(duration, "0"));
            List<String> updateList = new ArrayList<String>();
            RecordSet recordSet = new RecordSet();
            String sql = "select * from kq_balanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and resourceId=" + resourceId + " order by belongYear desc";
            if(distributionMode == 5){
                if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                        || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                    sql = "select * from kq_balanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and resourceId=" + resourceId + " and (expirationDate is null or expirationDate='' or expirationDate>='" + date + "') order by belongYear asc, expirationDate asc, id asc ";
                } else {
                    sql = "select * from kq_balanceOfLeave where (isDelete is null or isDelete<>1) and leaveRulesId=" + ruleId + " and resourceId=" + resourceId + " and (expirationDate is null or expirationDate>='" + date + "')  order by belongYear asc, expirationDate asc, id asc ";
                }
            }
            recordSet.executeQuery(sql);
            int total = recordSet.getCounts();
            int index = 0;
            logger.info("reduceUsedAmount:sql:"+sql);
            while (recordSet.next()) {
                index++;
                //
                String id = recordSet.getString("id");
                //所属年份
                String belongYear = recordSet.getString("belongYear");
                //已休
                BigDecimal usedAmount = new BigDecimal(Util.null2s(recordSet.getString("usedAmount"), "0"));
                //福利年假的已休
                BigDecimal usedAmount2 = new BigDecimal(Util.null2s(recordSet.getString("usedAmount2"), "0"));

                //记录使用记录
                usageHistoryEntity = new KQUsageHistoryEntity();
                usageHistoryEntity.setLeaveRulesId(ruleId);
                usageHistoryEntity.setRelatedId(resourceId);
                usageHistoryEntity.setWfRequestId(requestId);
                usageHistoryEntity.setOperator(resourceId);
                usageHistoryEntity.setOperateDate(currentDate);
                usageHistoryEntity.setOperateTime(currentTime);
                usageHistoryEntity.setOperateType("2");
                usageHistoryEntity.setInsertOrUpdate("update");
                usageHistoryEntity.setBelongYear(belongYear);
                usageHistoryEntity.setOldUsedAmount(usedAmount.setScale(5, RoundingMode.HALF_UP).toPlainString());
                usageHistoryEntity.setOldUsedAmount2(usedAmount2.setScale(5, RoundingMode.HALF_UP).toPlainString());
                usageHistoryEntity.setOldMinimumUnit("" + minimumUnit);
                usageHistoryEntity.setNewMinimumUnit("" + minimumUnit);

                if (distributionMode == 6) {//如果年假为混合模式（法定年假+福利年假）
                    if (priority == 1) {
                        BigDecimal temp = usedAmount.subtract(_duration);
                        logger.info("reduceUsedAmount:id1:"+id+":belongYear:"+belongYear+":usedAmount:"+usedAmount+":usedAmount2:"+usedAmount2+":temp1:"+temp);
                        if (temp.doubleValue() >= 0) {
                            String newUsedAmount = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                            String updateSql = "update kq_balanceOfLeave set usedAmount=" + newUsedAmount + " where id=" + id;
                            updateList.add(updateSql);

                            usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                            usageHistoryEntity.setNewUsedAmount2(usedAmount2.setScale(5, RoundingMode.HALF_UP).toPlainString());
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            usageHistoryEntityList.add(usageHistoryEntity);
                            break;
                        } else {
                            temp = usedAmount.add(usedAmount2).subtract(_duration);
                            logger.info("reduceUsedAmount:id2:"+id+":belongYear:"+belongYear+":usedAmount:"+usedAmount+":usedAmount2:"+usedAmount2+":temp2:"+temp);
                            /*此次销假流程的销假天数大于了他的已休假期，流程参数有误*/
                            if (index == total && temp.doubleValue() < 0) {
                                String newUsedAmount = "0";
                                String newUsedAmount2 = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=0,usedAmount2=" + newUsedAmount2 + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                usageHistoryEntityList.add(usageHistoryEntity);

                                logger.info("此次销假流程的销假天数大于了他的已休假期，流程参数有误。" +
                                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                                break;
                            }
                            if (temp.doubleValue() >= 0) {
                                String newUsedAmount = "0";
                                String newUsedAmount2 = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount=0,usedAmount2=" + newUsedAmount2 + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                usageHistoryEntityList.add(usageHistoryEntity);
                                break;
                            } else {
                                _duration = new BigDecimal("0").subtract(temp);
                                String updateSql = "update kq_balanceOfLeave set usedAmount=0,usedAmount2=0 where id=" + id;
                                updateList.add(updateSql);

                                if (usedAmount.doubleValue() != 0 || usedAmount2.doubleValue() != 0) {
                                    usageHistoryEntity.setNewUsedAmount("0");
                                    usageHistoryEntity.setNewUsedAmount2("0");
                                    usageHistoryEntity.setBalanceOfLeaveId(id);
                                    usageHistoryEntityList.add(usageHistoryEntity);
                                }
                                continue;
                            }
                        }
                    } else {
                        BigDecimal temp = usedAmount2.subtract(_duration);
                        if (temp.doubleValue() >= 0) {
                            String newUsedAmount2 = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                            String updateSql = "update kq_balanceOfLeave set usedAmount2=" + newUsedAmount2 + " where id=" + id;
                            updateList.add(updateSql);

                            usageHistoryEntity.setNewUsedAmount2(newUsedAmount2);
                            usageHistoryEntity.setNewUsedAmount(usedAmount.setScale(5, RoundingMode.HALF_UP).toPlainString());
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            usageHistoryEntityList.add(usageHistoryEntity);
                            break;
                        } else {
                            temp = usedAmount2.add(usedAmount).subtract(_duration);
                            /*此次销假流程的销假天数大于了他的已休假期，流程参数有误*/
                            if (index == total && temp.doubleValue() < 0) {
                                String newUsedAmount = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=0,usedAmount=" + newUsedAmount + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2("0.00");
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                usageHistoryEntityList.add(usageHistoryEntity);

                                logger.info("此次销假流程的销假天数大于了他的已休假期，流程参数有误。" +
                                        "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                                break;
                            }
                            if (temp.doubleValue() >= 0) {
                                String newUsedAmount = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                                String updateSql = "update kq_balanceOfLeave set usedAmount2=0,usedAmount=" + newUsedAmount + " where id=" + id;
                                updateList.add(updateSql);

                                usageHistoryEntity.setNewUsedAmount2("0.00");
                                usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                                usageHistoryEntity.setBalanceOfLeaveId(id);
                                usageHistoryEntityList.add(usageHistoryEntity);
                                break;
                            } else {
                                _duration = new BigDecimal("0").subtract(temp);
                                String updateSql = "update kq_balanceOfLeave set usedAmount=0,usedAmount2=0 where id=" + id;
                                updateList.add(updateSql);

                                if (usedAmount.doubleValue() != 0 || usedAmount2.doubleValue() != 0) {
                                    usageHistoryEntity.setNewUsedAmount2("0");
                                    usageHistoryEntity.setNewUsedAmount("0");
                                    usageHistoryEntity.setBalanceOfLeaveId(id);
                                    usageHistoryEntityList.add(usageHistoryEntity);
                                }
                                continue;
                            }
                        }
                    }
                } else {//非混合模式
                    BigDecimal temp = usedAmount.subtract(_duration);
                    /*此次销假流程的销假天数大于了他的已休假期，流程参数有误*/
                    if (index == total && temp.doubleValue() < 0) {
                        String newUsedAmount = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + newUsedAmount + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        usageHistoryEntityList.add(usageHistoryEntity);

                        logger.info("此次销假流程的销假天数大于了他的已休假期，流程参数有误。" +
                                "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                        break;
                    }
                    if (temp.doubleValue() >= 0) {
                        String newUsedAmount = temp.setScale(5, RoundingMode.HALF_UP).toPlainString();
                        String updateSql = "update kq_balanceOfLeave set usedAmount=" + newUsedAmount + " where id=" + id;
                        updateList.add(updateSql);

                        usageHistoryEntity.setNewUsedAmount(newUsedAmount);
                        usageHistoryEntity.setBalanceOfLeaveId(id);
                        usageHistoryEntityList.add(usageHistoryEntity);
                        break;
                    } else {
                        _duration = new BigDecimal("0").subtract(temp);
                        String updateSql = "update kq_balanceOfLeave set usedAmount=0 where id=" + id;
                        updateList.add(updateSql);

                        if (usedAmount.doubleValue() != 0) {
                            usageHistoryEntity.setNewUsedAmount("0");
                            usageHistoryEntity.setBalanceOfLeaveId(id);
                            usageHistoryEntityList.add(usageHistoryEntity);
                        }
                        continue;
                    }
                }
            }

            logger.info("requestId:"+requestId+"::updateList:"+updateList);
            /*SQL操作批处理*/
            for (int i = 0; i < updateList.size(); i++) {
                flag = recordSet.executeUpdate(updateList.get(i));
                if (!flag) {
                    logger.info("提交销假流程，回退员工假期余额SQL执行失败。" +
                            "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                    return;
                }
            }
            /*记录假期使用记录*/
            if (flag && usageHistoryEntityList.size() > 0) {
                KQUsageHistoryBiz usageHistoryBiz = new KQUsageHistoryBiz();
                flag = usageHistoryBiz.save(usageHistoryEntityList);
                if (!flag) {
                    logger.info("提交销假流程，员工假期余额变更记录SQL执行失败。" +
                            "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
                    return;
                }
            }
        } catch (Exception e) {
            logger.info(e.getMessage());
            logger.info("销假流程恢复假期余额报错。" +
                    "resourceId=" + resourceId + ",date=" + date + ",ruleId=" + ruleId + ",type=" + type + ",duration=" + duration + ",requestId=" + requestId);
            return;
        }
    }

    /**
     * 初始化系统内所有假期的假期余额
     */
    public static void createBalanceOfLeave() {
        try {
            Calendar today = Calendar.getInstance();
            String currentYear = Util.add0(today.get(Calendar.YEAR), 4);
            today.add(Calendar.YEAR,-1);
            String lastYear  = Util.add0(today.get(Calendar.YEAR), 4);
            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();
            kqLeaveRulesComInfo.setTofirstRow();
            while (kqLeaveRulesComInfo.next()) {
                if (!kqLeaveRulesComInfo.getIsEnable().equals("1") || !kqLeaveRulesComInfo.getBalanceEnable().equals("1")) {
                    continue;
                }
                createData(kqLeaveRulesComInfo.getId(), currentYear, 0, "", "1");

                // createData(kqLeaveRulesComInfo.getId(), lastYear, 0, "", "1");

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 批处理指定机构的指定假期的指定年份的假期余额
     *
     * @param leaveRulesId     指定假期类型的ID(对应的是数据库表kq_LeaveRules的主键ID)
     * @param belongYear       指定的所属年份(格式yyyy)
     * @param organizationType 机构类型：0-总部、1-分部、2-部门
     * @param organizationIds  指定机构的ID(例如当机构类型为分部时，此为分部ID)
     * @param operator         批处理的操作人员的人员ID(-1表示是系统定时任务自动批处理)
     * @return false--批处理失败、true--批处理成功
     */
    public static boolean createData(String leaveRulesId, String belongYear, int organizationType, String organizationIds, String operator) {
        return createData(leaveRulesId, belongYear, organizationType, organizationIds, operator, false);
    }

    /**
     * 批处理指定机构的指定假期的指定年份的假期余额
     *
     * @param ruleId           指定假期类型的ID(对应的是数据库表kq_LeaveRules的主键ID)
     * @param belongYear       指定的所属年份(格式yyyy)
     * @param organizationType 机构类型：0-总部、1-分部、2-部门
     * @param organizationIds  指定机构的ID(例如当机构类型为分部时，此为分部ID)
     * @param operator         批处理的操作人员的人员ID(-1表示是系统定时任务自动批处理)
     * @param canUpdate        是否按照规则更新员工的假期余额基数
     *                         因为员工的假期基数可以编辑，会出现和规则计算得出的值不一致的情况，只有当假期规则发生变动的情况时，才会按照最新的规则更新员工的假期基数
     * @return false--批处理失败、true--批处理成功
     */
    public static boolean createData(String ruleId, String belongYear, int organizationType, String organizationIds, String operator, boolean canUpdate) {
        boolean isSuccess = true;
        try {
            //假期类型缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假
            String minimumUnit = rulesComInfo.getMinimumUnit(ruleId);
            //假期类型的标识
            String leaveCode = rulesComInfo.getLeaveCode(ruleId);

            //假期规则缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //假期规则的ID
            String rulesDetailId = "";
            //余额发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放、7-按司龄自动发放(入职日期当天发放余额)
            int distributionMode = 1;
            //每人发放小时(天)数(当余额发放方式为每年自动发放固定天数时有效)
            double annualAmount = 0;
            //法定年假规则(当distributionMode=6时有效)：0-工龄、1-司龄、2-工龄+司龄
            int legalKey = 0;
            //福利年假规则(当distributionMode=6时有效)：0-工龄、1-司龄、2-工龄+司龄
            int welfareKey = 1;
            //年假基数计算方式：
            // 0-假期基数发放日期和假期基数变动日期均为每年的01月01号(但是假期基数是结合 司龄/工龄 变化前后的值按天数比例折算出来的)、
            // 1-假期基数发放日期和假期基数变动日期均为每年的01月01号、
            // 2-假期基数发放日期发放日期为每年的01月01号，假期基数的变动日期为每年的 入职日期/参加工作日期
            int calcMethod = 0;
            //假期基数的折算方式
            int convertMode = 0;
            //假期基数的小数位数
            int decimalDigit = 2;
            //次账号是否能享受此假期：0-排除，即次账号不能享受此假期、1-不排除，即次账号正常享受此假期
            int excludeSubAccount = 1;
            //转正之前是否能享受此假期：0-不允许、1-允许
            int beforeFormal = 1;

            //假期基数
            double baseAmount = 0.00;
            //福利年假(当distributionMode=6时才有作用)
            double baseAmount2 = 0.00;
            //系统内原来的假期基数的合集
            Map<String, Object> oldBalanceEntities = getBalanceEntities(organizationType, organizationIds, ruleId, belongYear);
            //员工假期余额实体类
            KQBalanceOfLeaveEntity oldBalanceEntity = null;
            //此次批处理新计算的假期基数的合集
            List<KQBalanceOfLeaveEntity> newBalanceEntities = new ArrayList<KQBalanceOfLeaveEntity>();
            //员工假期余额实体类
            KQBalanceOfLeaveEntity newBalanceEntity = null;
            //
            Map<String, Object> params = null;
            //今天的日期
            String currentDate = DateUtil.getCurrentDate();

            ManageDetachComInfo manageDetachComInfo = new ManageDetachComInfo();
            //是否开启了人力资源模块的管理分权
            boolean isUseHrmManageDetach = manageDetachComInfo.isUseHrmManageDetach();

            boolean addOrUpdate = false;
            String expirationDate =DateUtil.getCurrentDate();
            String belongYearTemp = belongYear;
            String belongYearNew ="";

            String sql = " select * from HrmResource where status in (0,1,2,3) ";
            if (organizationType == 1) {
                sql += " and subCompanyId1 in (" + organizationIds + ") ";
            } else if (organizationType == 2) {
                sql += " and departmentId in (" + organizationIds + ") ";
            } else if (organizationType == 3) {
                sql += " and id in (" + organizationIds + ") ";
            }
            //如果开启了分权
            if (isUseHrmManageDetach) {
                SubCompanyComInfo subCompanyComInfo = new SubCompanyComInfo();
                String allRightSubComIds = subCompanyComInfo.getRightSubCompany(Util.getIntValue(operator,1),"KQLeaveRulesEdit:Edit",0);
                sql += " and " + Util.getSubINClause(allRightSubComIds, "subCompanyId1", "in");
            } else {
                if (!operator.equals("1")) {
                    User user = new User(Util.getIntValue(operator));
                    String rightLevel = HrmUserVarify.getRightLevel("KQLeaveRulesEdit:Edit", user);
                    int departmentID = user.getUserDepartment();
                    int subcompanyID = user.getUserSubCompany1();

                    if (rightLevel.equals("2")) {
                        // 总部级别的，什么也不返回
                    } else if (rightLevel.equals("1")) { // 分部级别的
                        sql += " and subCompanyId1=" + subcompanyID;
                    } else if (rightLevel.equals("0")) { // 部门级别
                        sql += " and departmentId=" + departmentID;
                    }
                }
            }
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(sql);
            while (recordSet.next()) {
                /*获取人员的相关信息 start*/
                String resourceId = recordSet.getString("id");
                String accountType = Util.null2String(recordSet.getString("accountType"));
                String status = Util.null2String(recordSet.getString("status"));
                String createDate = Util.null2String(recordSet.getString("createDate"));
                String workStartDate = Util.null2String(recordSet.getString("workStartDate"));
                String companyStartDate = Util.null2String(recordSet.getString("companyStartDate"));
                //如果参加工作日期为空，则默认采用创建日期
                workStartDate = (workStartDate.equals("")||workStartDate.length()<10||workStartDate.indexOf("-")<=0) ? createDate : workStartDate;
                //如果入职日期为空，则默认采用创建日期
                companyStartDate = (companyStartDate.equals("")||companyStartDate.length()<10||companyStartDate.indexOf("-")<=0) ? createDate : companyStartDate;
                String subCompanyId = Util.null2String(recordSet.getString("subCompanyId1"));
                /*获取人员的相关信息 end*/

                /*获取人员所属分部对应的假期规则 start*/
                rulesDetailId = detailComInfo.getId(ruleId, subCompanyId);
                distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subCompanyId), 1);
                legalKey = Util.getIntValue(detailComInfo.getLegalKey(ruleId, subCompanyId), 0);
                welfareKey = Util.getIntValue(detailComInfo.getWelfareKey(ruleId, subCompanyId), 1);
                annualAmount = Util.getDoubleValue(detailComInfo.getAnnualAmount(ruleId, subCompanyId), 0.00);
                calcMethod = Util.getIntValue(detailComInfo.getCalcMethod(ruleId, subCompanyId), 0);
                convertMode = Util.getIntValue(detailComInfo.getConvertMode(ruleId, subCompanyId), 0);
                decimalDigit = Util.getIntValue(detailComInfo.getDecimalDigit(ruleId, subCompanyId), 2);
                excludeSubAccount = Util.getIntValue(detailComInfo.getExcludeSubAccount(ruleId, subCompanyId), 1);
                beforeFormal = Util.getIntValue(detailComInfo.getBeforeFormal(ruleId, subCompanyId), 1);
                /*获取人员所属分部对应的假期规则 end*/

                //封装参数 start
                params = new HashMap<String, Object>();
                params.put("resourceId", resourceId);
                params.put("status", status);
                params.put("createDate", createDate);
                params.put("workStartDate", workStartDate);
                params.put("companyStartDate", companyStartDate);
                params.put("subCompanyId", subCompanyId);

                params.put("belongYear", belongYear);
                params.put("rulesDetailId", rulesDetailId);
                params.put("distributionMode", distributionMode);
                params.put("annualAmount", annualAmount);
                params.put("legalKey", legalKey);
                params.put("welfareKey", welfareKey);
                params.put("calcMethod", calcMethod);
                params.put("convertMode", convertMode);
                params.put("decimalDigit", decimalDigit);
                params.put("excludeSubAccount", excludeSubAccount);
                params.put("beforeFormal", beforeFormal);
                //封装参数 end

                if (distributionMode == 1) {
                    continue;
                } else if (distributionMode == 2 || distributionMode == 3) {
                    baseAmount = getBaseAmount(params);
                } else if (distributionMode == 4) {
                    baseAmount = getBaseAmountByDis4(params);
                } else if (distributionMode == 5) {
                    continue;
                } else if (distributionMode == 6) {
                    params.put("legalOrWelfare", "legal");
                    baseAmount = getBaseAmountByDis6(params);
                    params.put("legalOrWelfare", "welfare");
                    baseAmount2 = getBaseAmountByDis6(params);
                } else if (distributionMode == 7) {
                    baseAmount = getBaseAmountByDis7(params);
                } else if (distributionMode == 8) {
                    Map<String, Object>  baseAmountMaps = getBaseAmountByDis8(params);
                    baseAmount =  (Double) baseAmountMaps.get("baseAmount");
                    addOrUpdate = (boolean) baseAmountMaps.get("addOrUpdate");
                    expirationDate = (String) baseAmountMaps.get("expirationDate");
                    belongYearNew = (String) baseAmountMaps.get("belongYearNew");
                    if(addOrUpdate&&!belongYearNew.equals("")){
                        belongYear = belongYearNew;
                    }
                }

                if (beforeFormal == 0 && !status.equals("1")) {
                    //转正前不允许发放余额
                    if (canUpdate) {
                        baseAmount = 0;
                        baseAmount2 = 0;
                    } else {
                        continue;
                    }
                }
                if (excludeSubAccount == 0 && accountType.equals("1")) {
                    //次账号不允许发放余额
                    if (canUpdate) {
                        baseAmount = 0;
                        baseAmount2 = 0;
                    } else {
                        continue;
                    }
                }

                oldBalanceEntity = (KQBalanceOfLeaveEntity) oldBalanceEntities.get(resourceId);
                if (oldBalanceEntity != null) {
                    //如果假期基数没有发生变化，不需要更新
                    if (!addOrUpdate&&oldBalanceEntity.getBaseAmount() == baseAmount && oldBalanceEntity.getBaseAmount2() == baseAmount2) {
                        continue;
                    }
                    //如果是系统自动批处理(默认系统自动批处理不允许修改假期余额基数)
                    if (!canUpdate) {
                        //但是如果假期基数、额外、已休都是0，允许重新计算
                        if (oldBalanceEntity.getBaseAmount() > 0 || oldBalanceEntity.getExtraAmount() > 0 || oldBalanceEntity.getUsedAmount() > 0
                                || oldBalanceEntity.getBaseAmount2() > 0 || oldBalanceEntity.getExtraAmount2() > 0 || oldBalanceEntity.getUsedAmount2() > 0) {
                            if (calcMethod == 2) {
                                //但是如果【假期基数计算方式】选择【按最多的计算】时，允许在【工龄or司龄变动日期】更新假期基数
                                if (distributionMode == 2) {
                                    String ageLimitChangeDate = belongYear + companyStartDate.substring(4);
                                    if (currentDate.compareTo(ageLimitChangeDate) < 0) {
                                        continue;
                                    }
                                } else if (distributionMode == 3) {
                                    String ageLimitChangeDate = belongYear + workStartDate.substring(4);
                                    if (currentDate.compareTo(ageLimitChangeDate) < 0) {
                                        continue;
                                    }
                                } else if (distributionMode == 6) {
                                    String ageLimitChangeDate = belongYear + workStartDate.substring(4);
                                    String ageLimitChangeDate2 = belongYear + companyStartDate.substring(4);
                                    if (currentDate.compareTo(ageLimitChangeDate) < 0 && currentDate.compareTo(ageLimitChangeDate2) < 0) {
                                        continue;
                                    }
                                }
                            }  else {
                                if (distributionMode == 6) {
                                    String ageLimitChangeDate = belongYear + workStartDate.substring(4);
                                    String ageLimitChangeDate2 = belongYear + companyStartDate.substring(4);
                                    if (currentDate.compareTo(ageLimitChangeDate) < 0 && currentDate.compareTo(ageLimitChangeDate2) < 0) {
                                        continue;
                                    }
                                }else{
                                    if(!addOrUpdate){
                                        continue;
                                    }
                                }
                            }
                            if(addOrUpdate){
                                if (currentDate.compareTo(expirationDate) != 0) {
                                    continue;
                                }
                            }
                        }
                    }
                } else {
                    //如果没有生成过假期余额，但是此次生成的假期余额如果为0，则不新增，直接跳过
                    if (baseAmount == 0 && baseAmount2 == 0) {
                        continue;
                    }
                }

                newBalanceEntity = new KQBalanceOfLeaveEntity();
                newBalanceEntity.setResourceId(Util.getIntValue(resourceId));
                newBalanceEntity.setLeaveRulesId(Util.getIntValue(ruleId));
                newBalanceEntity.setBelongYear(belongYear);
                newBalanceEntity.setBaseAmount(baseAmount);
                newBalanceEntity.setBaseAmount2(baseAmount2);
                newBalanceEntity.setMinimumUnit(Util.getIntValue(minimumUnit));
                if(addOrUpdate){
                    newBalanceEntity.setUsedAmount(baseAmount);
                    newBalanceEntity.setExtraAmount(baseAmount);
                    newBalanceEntity.setStatus(1);
                }else{
                    newBalanceEntity.setStatus(0);
                }
                newBalanceEntities.add(newBalanceEntity);
                belongYear = belongYearTemp;
            }
            isSuccess = insertOrUpdateBalance(newBalanceEntities, oldBalanceEntities, operator, canUpdate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return isSuccess;
    }

    /**
     * 获取指定机构下的人员的假期余额
     *
     * @param organizationType 指定机构类型：0-总部、1-分部、2-部门、3-热力资源
     * @param organizationIds  指定机构的ID，用英文逗号分隔
     * @param ruleId           假期类型的ID
     * @param belongYear       所属年份
     * @return
     */
    private static Map<String, Object> getBalanceEntities(int organizationType, String organizationIds, String ruleId, String belongYear) {
        Map<String, Object> balanceEntities = new HashMap<String, Object>();
        try {
            KQBalanceOfLeaveEntity balanceEntity = null;

            //假期类型缓存类
            KQLeaveRulesComInfo rulesComInfo = new KQLeaveRulesComInfo();
            //最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假
            String minimumUnit = rulesComInfo.getMinimumUnit(ruleId);

            RecordSet recordSet = new RecordSet();
            String sql = "select * from kq_BalanceOfLeave where leaveRulesId=" + ruleId + " and belongYear=" + belongYear;
            if (organizationType == 1) {
                sql += " and resourceId in (select id from HrmResource where subCompanyId1 in (" + organizationIds + "))";
            } else if (organizationType == 2) {
                sql += " and resourceId in (select id from HrmResource where departmentId in (" + organizationIds + "))";
            } else if (organizationType == 3) {
                sql += " and resourceId in (" + organizationIds + ")";
            }
            recordSet.executeQuery(sql);
            while (recordSet.next()) {
                int id = recordSet.getInt("id");
                int resourceId = recordSet.getInt("resourceId");
                int leaveRuleId = recordSet.getInt("leaveRulesId");
                double baseAmount = Util.getDoubleValue(recordSet.getString("baseAmount"), 0.00);
                double extraAmount = Util.getDoubleValue(recordSet.getString("extraAmount"), 0.00);
                double usedAmount = Util.getDoubleValue(recordSet.getString("usedAmount"), 0.00);
                double baseAmount2 = Util.getDoubleValue(recordSet.getString("baseAmount2"), 0.00);
                double extraAmount2 = Util.getDoubleValue(recordSet.getString("extraAmount2"), 0.00);
                double usedAmount2 = Util.getDoubleValue(recordSet.getString("usedAmount2"), 0.00);

                balanceEntity = new KQBalanceOfLeaveEntity();
                balanceEntity.setId(id);
                balanceEntity.setResourceId(resourceId);
                balanceEntity.setBelongYear(belongYear);
                balanceEntity.setLeaveRulesId(leaveRuleId);
                balanceEntity.setBaseAmount(baseAmount);
                balanceEntity.setExtraAmount(extraAmount);
                balanceEntity.setUsedAmount(usedAmount);
                balanceEntity.setBaseAmount2(baseAmount2);
                balanceEntity.setExtraAmount2(extraAmount2);
                balanceEntity.setUsedAmount2(usedAmount2);
                balanceEntity.setMinimumUnit(Util.getIntValue(minimumUnit));

                balanceEntities.put("" + resourceId, balanceEntity);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return balanceEntities;
    }

    /**
     * 获取指定人员的指定假期的指定年份的假期基数发放日期
     *
     * @param resourceId 指定人员的人员ID
     * @param ruleId     指定假期类型的ID
     * @param belongYear 指定年份
     * @return
     */
    private static Calendar getReleaseDate(String resourceId, String ruleId, String belongYear) {
        Calendar calendar = null;
        try {
            //人员缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);
            //创建日期
            String createDate = resourceComInfo.getCreatedate(resourceId);
            //入职日期
            String companyStartDate = resourceComInfo.getCompanyStartDate(resourceId);
            //没有维护入职日期时取创建日期
            companyStartDate = companyStartDate.equals("") ? createDate : companyStartDate;
            //参加工作日期
            String workStartDate = resourceComInfo.getWorkStartDate(resourceId);
            //没有维护参加工作日期时取创建日期
            workStartDate = workStartDate.equals("") ? createDate : workStartDate;

            /**********************************************************************************************************/

            //假期规则的缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //假期规则的ID
            String rulesDetailId = Util.null2String(detailComInfo.getId(ruleId, subCompanyId));
            //余额发放方式
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subCompanyId), 1);
            //年假基数计算方式：
            // 0-假期基数发放日期和假期基数变动日期均为每年的01月01号(但是假期基数是结合 司龄/工龄 变化前后的值按天数比例折算出来的)、
            // 1-假期基数发放日期和假期基数变动日期均为每年的01月01号、
            // 2-假期基数发放日期发放日期为每年的01月01号，假期基数的变动日期为每年的 入职日期/参加工作日期
            int calcMethod = Util.getIntValue(detailComInfo.getCalcMethod(ruleId, subCompanyId), 1);

            /***********************************************************************************************************/

            //假期基数发放日期
            String baseAmountReleaseDate = "";
            //工龄or司令的变动日期
            String ageLimitChangeDate = "";
            //入职日期or参加工作日期
            String date4CalcAgeLimit = "";
            if (distributionMode == 1) {
                baseAmountReleaseDate = belongYear + "-01-01";
                //默认情况下都是以【假期基数发放日期】作为释放的开始日期
                calendar = DateUtil.getCalendar(baseAmountReleaseDate);
                //如果批处理年份等于入职年份，应该以入职日期作为释放的开始日期
                if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                    calendar = DateUtil.getCalendar(companyStartDate);
                }
                return calendar;
            } else if (distributionMode == 2) {
                date4CalcAgeLimit = companyStartDate;
                //根据假期基数的计算方式得出【假期基数发放日期】、【入职年限/参加工作年限 的变动日期】
                if (calcMethod == 0 || calcMethod == 1) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    ageLimitChangeDate = belongYear + companyStartDate.substring(4);
                } else if (calcMethod == 2) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    ageLimitChangeDate = belongYear + companyStartDate.substring(4);
                }
            } else if (distributionMode == 3) {
                date4CalcAgeLimit = workStartDate;
                //根据假期基数的计算方式得出【假期基数发放日期】、【入职年限/参加工作年限 的变动日期】
                if (calcMethod == 0 || calcMethod == 1) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    ageLimitChangeDate = belongYear + workStartDate.substring(4);
                } else if (calcMethod == 2) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    ageLimitChangeDate = belongYear + workStartDate.substring(4);
                }
            } else if (distributionMode == 4) {
                baseAmountReleaseDate = belongYear + "-01-01";
                //默认情况下都是以【假期基数发放日期】作为释放的开始日期
                calendar = DateUtil.getCalendar(baseAmountReleaseDate);
                //如果批处理年份等于入职年份，应该以入职日期作为释放的开始日期
                if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                    calendar = DateUtil.getCalendar(companyStartDate);
                }
                return calendar;
            }

            //人员在【假期基数发放日期】时对应的【工龄or司龄】
            int ageLimit = getAgeLimit(date4CalcAgeLimit, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时应有的【假期基数】
            BigDecimal baseAmount_releaseDate = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit, -1, -1, -1, "");

            //人员在【工龄or司龄变动日期】时对应的【工龄or司龄】
            int ageLimit_ageLimitChangeDate = getAgeLimit(date4CalcAgeLimit, ageLimitChangeDate);
            //人员在【工龄or司龄变动日期】时应有的【假期基数】
            BigDecimal baseAmount_ageLimitChangeDate = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_ageLimitChangeDate, -1, -1, -1, "");

            //默认情况下都是以【假期基数发放日期】作为释放的开始日期
            calendar = DateUtil.getCalendar(baseAmountReleaseDate);
            //如果批处理年份等于入职年份，应该以入职日期作为释放的开始日期
            if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                calendar = DateUtil.getCalendar(companyStartDate);
            }
            //如果是初始获得年(即年初01-01的时候假期基数为0，但是后面工龄or司龄增加后，假期基数就不再为0了)。应该以【工龄or司龄变动日期】作为释放的开始日期
            if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate.doubleValue() > 0) {
                calendar = DateUtil.getCalendar(ageLimitChangeDate);
                //比如：参加工作日是：2020-02-01，入职日期是：2021-08-16，没有该处理会按：2021-02-01给用户计算“已释放”数值
                if(belongYear.compareTo(companyStartDate.substring(0, 4)) == 0 && ageLimitChangeDate.compareTo(companyStartDate)<0){
                    calendar = DateUtil.getCalendar(companyStartDate);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return calendar;
    }

    /**
     * [6-按司龄+工龄自动发放]获取指定人员的指定假期的指定年份的假期基数发放日期
     *
     * @param resourceId     指定人员的人员ID
     * @param ruleId         指定假期类型的ID
     * @param belongYear     指定年份
     * @param legalOrWelfare 是计算[法定年假]还是计算[福利年假]：legal-法定年假、welfare-福利年假
     * @return
     */
    private static Calendar getReleaseDateByDis6(String resourceId, String ruleId, String belongYear, String legalOrWelfare) {
        Calendar calendar = null;
        try {
            //人员缓存类
            ResourceComInfo resourceComInfo = new ResourceComInfo();
            //分部ID
            String subCompanyId = resourceComInfo.getSubCompanyID(resourceId);
            //创建日期
            String createDate = resourceComInfo.getCreatedate(resourceId);
            //入职日期
            String companyStartDate = resourceComInfo.getCompanyStartDate(resourceId);
            //没有维护入职日期时取创建日期
            companyStartDate = companyStartDate.equals("") ? createDate : companyStartDate;
            //参加工作日期
            String workStartDate = resourceComInfo.getWorkStartDate(resourceId);
            //没有维护参加工作日期时取创建日期
            workStartDate = workStartDate.equals("") ? createDate : workStartDate;

            /***********************************************************************************************************/

            //假期规则的缓存类
            KQLeaveRulesDetailComInfo detailComInfo = new KQLeaveRulesDetailComInfo();
            //假期规则的ID
            String rulesDetailId = Util.null2String(detailComInfo.getId(ruleId, subCompanyId));
            //余额发放方式
            int distributionMode = Util.getIntValue(detailComInfo.getDistributionMode(ruleId, subCompanyId), 1);
            //年假基数计算方式：
            // 0-假期基数发放日期和假期基数变动日期均为每年的01月01号(但是假期基数是结合 司龄/工龄 变化前后的值按天数比例折算出来的)、
            // 1-假期基数发放日期和假期基数变动日期均为每年的01月01号、
            // 2-假期基数发放日期发放日期为每年的01月01号，假期基数的变动日期为每年的 入职日期/参加工作日期
            int calcMethod = Util.getIntValue(detailComInfo.getCalcMethod(ruleId, subCompanyId), 1);
            //
            int legalKey = Util.getIntValue(detailComInfo.getLegalKey(ruleId, subCompanyId), 0);
            //
            int welfareKey = Util.getIntValue(detailComInfo.getWelfareKey(ruleId, subCompanyId), 1);

            /***********************************************************************************************************/

            //假期基数发放日期
            String baseAmountReleaseDate = "";
            //工龄变动日期
            String ageLimitChangeDate = "";
            //司龄变动日期
            String ageLimitChangeDate2 = "";
            //根据假期基数的计算方式得出【假期基数发放日期】、【假期基数变动日期】、【工龄变动日期】、【司龄变动日期】
            if (calcMethod == 0 || calcMethod == 1) {
                baseAmountReleaseDate = belongYear + "-01-01";
                ageLimitChangeDate = belongYear + workStartDate.substring(4);
                ageLimitChangeDate2 = belongYear + companyStartDate.substring(4);
            } else if (calcMethod == 2) {
                baseAmountReleaseDate = belongYear + "-01-01";
                ageLimitChangeDate = belongYear + workStartDate.substring(4);
                ageLimitChangeDate2 = belongYear + companyStartDate.substring(4);
            }

            //人员在【假期基数发放日期】时对应的工龄
            int ageLimit_releaseDate = getAgeLimit(workStartDate, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时对应的司龄
            int ageLimit2_releaseDate = getAgeLimit(companyStartDate, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时对应的假期基数
            BigDecimal baseAmount_releaseDate = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_releaseDate, ageLimit2_releaseDate, legalKey, welfareKey, legalOrWelfare);

            //人员在【工龄变动日期】时对应的工龄
            int ageLimit_ageLimitChangeDate = getAgeLimit(workStartDate, ageLimitChangeDate);
            //人员在【工龄变动日期】时对应的司龄
            int ageLimit2_ageLimitChangeDate = getAgeLimit(companyStartDate, ageLimitChangeDate);
            //人员在【工龄变动日期】时对应的假期基数
            BigDecimal baseAmount_ageLimitChangeDate = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_ageLimitChangeDate, ageLimit2_ageLimitChangeDate, legalKey, welfareKey, legalOrWelfare);

            //人员在【司龄变动日期】时对应的司龄
            int ageLimit_ageLimitChangeDate2 = getAgeLimit(workStartDate, ageLimitChangeDate2);
            //人员在【司龄变动日期】时对应的司龄
            int ageLimit2_ageLimitChangeDate2 = getAgeLimit(companyStartDate, ageLimitChangeDate2);
            //人员在【司龄变动日期】时对应的假期基数
            BigDecimal baseAmount_ageLimitChangeDate2 = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_ageLimitChangeDate2, ageLimit2_ageLimitChangeDate2, legalKey, welfareKey, legalOrWelfare);

            //默认情况下都是以【假期基数发放日期】作为释放的开始日期
            calendar = DateUtil.getCalendar(baseAmountReleaseDate);
            //如果批处理年份等于入职年份，应该以入职日期作为释放的开始日期
            if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                calendar = DateUtil.getCalendar(companyStartDate);
            }
            //如果是初始获得年(即年初01-01的时候假期基数为0，但是后面工龄or司龄增加后，假期基数就不再为0了)
            if (ageLimitChangeDate.compareTo(ageLimitChangeDate2) <= 0) {
                if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate.doubleValue() > 0) {
                    calendar = DateUtil.getCalendar(ageLimitChangeDate);
                }
                if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate2.doubleValue() > 0) {
                    calendar = DateUtil.getCalendar(ageLimitChangeDate2);
                }
            } else {
                if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate2.doubleValue() > 0) {
                    calendar = DateUtil.getCalendar(ageLimitChangeDate2);
                }
                if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate2.doubleValue() <= 0 && baseAmount_ageLimitChangeDate.doubleValue() > 0) {
                    calendar = DateUtil.getCalendar(ageLimitChangeDate2);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return calendar;
    }

    /**
     * 计算假期基数
     *
     * @param params Map<String,Object>
     *               belongYear:所属年份
     *               createDate:人员创建日期
     *               workStartDate:参加工作日期
     *               companyStartDate:入职日期
     *               rulesDetailId:假期规则的ID
     *               distributionMode:当前假期规则发放方式
     *               annualAmount:当[distributionMode]=[4-每年发放固定天数]时有效
     *               legalKey:法定年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               welfareKey:福利年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               calcMethod:假期基数计算方式
     *               convertMode:折算方式
     *               decimalDigit:折算后的假期余额保留几位小数
     * @return
     */
    private static double getBaseAmount(Map<String, Object> params) {
        BigDecimal baseAmount = new BigDecimal("0");
        int decimalDigit = 2;
        try {
            //当前日期
            String currentDate = DateUtil.getCurrentDate();
            //当前年份
            String currentYear = currentDate.substring(0, 4);
            //批处理年份
            String belongYear = Util.null2String(params.get("belongYear"));
            //人员的参加工作日期
            String workStartDate = Util.null2String(params.get("workStartDate"));
            //人员的入职日期
            String companyStartDate = Util.null2String(params.get("companyStartDate"));

            //假期规则的ID
            String rulesDetailId = Util.null2String(params.get("rulesDetailId"));
            //当前假期规则发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue("" + params.get("distributionMode"), 1);
            //当[distributionMode]=[4-每年发放固定天数]时有效
            double annualAmount = Util.getDoubleValue("" + params.get("annualAmount"), 0);
            //法定年假规则(当distributionMode=6时有效)：0-工龄、1-司龄、2-工龄+司龄
            int legalKey = Util.getIntValue("" + params.get("legalKey"), 0);
            //福利年假规则(当distributionMode=6时有效)：0-工龄、1-司龄、2-工龄+司龄
            int welfareKey = Util.getIntValue("" + params.get("welfareKey"), 1);
            //年假基数计算方式：
            // 0-假期基数发放日期和假期基数变动日期均为每年的01月01号(但是假期基数是结合 司龄/工龄 变化前后的值按天数比例折算出来的)、
            // 1-假期基数发放日期和假期基数变动日期均为每年的01月01号、
            // 2-假期基数发放日期发放日期为每年的01月01号，假期基数的变动日期为每年的 入职日期/参加工作日期
            int calcMethod = Util.getIntValue("" + params.get("calcMethod"), 1);
            //假期基数发放日期
            String baseAmountReleaseDate = "";
            //假期基数变动日期
            String baseAmountChangeDate = "";
            //工龄or司龄的变动日期
            String ageLimitChangeDate = "";
            String ageLimitChangeDatetemp = "";
            //入职日期or参加工作日期
            String date4CalcAgeLimit = "";
            if (distributionMode == 2) {
                date4CalcAgeLimit = companyStartDate;
                //根据假期基数的计算方式得出【假期基数发放日期】、【假期基数变动日期】、【工龄or司龄的变动日期】
                if (calcMethod == 0 || calcMethod == 1) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    baseAmountChangeDate = belongYear + "-01-01";
                    ageLimitChangeDate = belongYear + companyStartDate.substring(4);
                } else if (calcMethod == 2) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    baseAmountChangeDate = belongYear + companyStartDate.substring(4);
                    ageLimitChangeDate = belongYear + companyStartDate.substring(4);
                }
            } else if (distributionMode == 3) {
                date4CalcAgeLimit = workStartDate;
                //根据假期基数的计算方式得出【假期基数发放日期】、【假期基数变动日期】、【工龄or司龄的变动日期】
                if (calcMethod == 0 || calcMethod == 1) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    baseAmountChangeDate = belongYear + "-01-01";
                    ageLimitChangeDate = belongYear + workStartDate.substring(4);
                } else if (calcMethod == 2) {
                    baseAmountReleaseDate = belongYear + "-01-01";
                    baseAmountChangeDate = belongYear + workStartDate.substring(4);
                    ageLimitChangeDate = belongYear + workStartDate.substring(4);
                }
                //如果是初始获得年，那么，入职日期和参加工作日期，取相对较晚的日期，作为变动日期
                if(companyStartDate.substring(4).compareTo(workStartDate.substring(4)) <= 0){
                    ageLimitChangeDatetemp = belongYear + workStartDate.substring(4);
                }else{
                    ageLimitChangeDatetemp = belongYear + companyStartDate.substring(4);
                }
            }

            //批处理日期（因为存在当前年份批处理上一年份或者批处理下一年份，所以需要做一些特殊处理）
            String date4CalcAmount = "";
            if (belongYear.compareTo(currentYear) < 0) {
                date4CalcAmount = belongYear + "-12-31";
            } else if (belongYear.compareTo(currentYear) == 0) {
                date4CalcAmount = currentDate;
            } else {
                date4CalcAmount = baseAmountReleaseDate;
            }
            //折算方式
            int convertMode = Util.getIntValue("" + params.get("convertMode"), 0);
            //折算后的假期余额保留几位小数
            decimalDigit = Util.getIntValue("" + params.get("decimalDigit"), 2);

            //人员在【假期基数发放日期】时对应的【工龄or司龄】
            int ageLimit = getAgeLimit(date4CalcAgeLimit, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时应有的【假期基数】
            BigDecimal baseAmount_releaseDate = getAmountByAgeLimit(distributionMode, rulesDetailId, annualAmount, ageLimit, -1, -1, -1, "");

            //人员在【工龄or司龄变动日期】时对应的【工龄or司龄】
            int ageLimit_ageLimitChangeDate = getAgeLimit(date4CalcAgeLimit, ageLimitChangeDate);
            //人员在【工龄or司龄变动日期】时应有的【假期基数】
            BigDecimal baseAmount_ageLimitChangeDate = getAmountByAgeLimit(distributionMode, rulesDetailId, annualAmount, ageLimit_ageLimitChangeDate, -1, -1, -1, "");

            //如果人员在【假期基数发放日期】时对应的【假期基数】为0，并且【批处理日期】小于【工龄or司龄变动日期】，则假期基数应该是0
            if (baseAmount_releaseDate.doubleValue() <= 0 && date4CalcAmount.compareTo(ageLimitChangeDate) < 0) {
                return 0;
            }

            //如果【calcMethod】==0？假期基数按比例精确计算
            boolean needCalc0 = false;
            //是否需要折算
            boolean needConvert = false;
            //以哪个日期来折算
            Calendar calendar = null;
            //入职前的假期基数应该为0
            if (date4CalcAmount.compareTo(companyStartDate) < 0) {
                return 0;
            }
            //如果批处理年份等于入职年份，那应该以【入职日期】来折算
            if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                //需要折算
                needConvert = true;
                //以【入职日期来折算】
                calendar = DateUtil.getCalendar(companyStartDate);
            }
            /**
             * 年假基数的计算方法如下：
             * 第一种情况：工龄or司龄的变动日期<假期基数发放日期<=假期基数变动日期
             *      批处理日期<假期基数发放日期？假期基数=0
             *      批处理日期>=假期基数基数发放日期？根据假期基数发放日期计算工龄or司龄，然后得出对应的假期基数
             * 第二种情况：假期基数发放日期<=工龄or司龄的变动日期<=假期基数变动日期
             *      批处理日期<假期基数发放日期？假期基数=0
             *      批处理日期=假期基数发放日期？根据假期基数发放日期计算工龄or司龄，然后得出对应的假期基数
             *      批处理日期>假期基数发放日期 && 批处理日期<工龄or司龄的变动日期？根据假期基数发放日期计算工龄or司龄，然后得出对应的假期基数
             *      批处理日期>假期基数发放日期 && 批处理日期>=工龄or司龄的变动日期？
             *          如果是初始获得年(即年初01-01的时候假期基数为0，但是后面工龄or司龄增加后，假期基数就不再为0了)？根据【工龄or司龄的变动日期】计算工龄or司龄，然后得出对应的假期基数。此处得到的假期基数需要折算）
             *          如果不是初始获得年？批处理日期>=假期基数变动日期？根据【工龄or司龄的变动日期】计算工龄or司龄，然后得出对应的假期基数。此处得到的假期基数不需要折算
             * 第三种情况：假期基数发放日期<假期基数变动日期<工龄or司龄的变动日期
             *      批处理日期<假期基数发放日期？假期基数=0
             *      批处理日期=假期基数发放日期？
             *          如果【calcMethod】!=0？根据假期基数发放日期计算工龄or司龄，然后得出对应的假期基数
             *          如果【calcMethod】==0？假期基数按比例精确计算
             *      批处理日期>假期基数发放日期 && 批处理日期<工龄or司龄的变动日期？
             *          如果【calcMethod】!=0？根据假期基数发放日期计算工龄or司龄，然后得出对应的假期基数
             *          如果【calcMethod】==0？假期基数按比例精确计算
             *      批处理日期>假期基数发放日期 && 批处理日期>=工龄or司龄的变动日期？
             *          如果是初始获得年？根据【工龄or司龄的变动日期】计算工龄or司龄，然后得出对应的假期基数。此处得到的假期基数需要折算
             *          如果不是初始获得年？
             *              如果【calcMethod】!=0？根据假期基数发放日期计算工龄or司龄，然后得出对应的假期基数
             *              如果【calcMethod】==0？假期基数按比例精确计算
             */
            if (ageLimitChangeDate.compareTo(baseAmountReleaseDate) < 0 && baseAmountReleaseDate.compareTo(baseAmountChangeDate) <= 0) {
                if (date4CalcAmount.compareTo(baseAmountReleaseDate) < 0) {
                    return 0;
                } else if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                    baseAmount = baseAmount_releaseDate;
                }
            } else if (baseAmountReleaseDate.compareTo(ageLimitChangeDate) <= 0 && ageLimitChangeDate.compareTo(baseAmountChangeDate) <= 0) {
                if (date4CalcAmount.compareTo(baseAmountReleaseDate) < 0) {
                    return 0;
                } else if (date4CalcAmount.compareTo(baseAmountReleaseDate) == 0) {
                    baseAmount = baseAmount_releaseDate;
                } else {
                    if (date4CalcAmount.compareTo(ageLimitChangeDate) < 0) {
                        baseAmount = baseAmount_releaseDate;
                    } else if (date4CalcAmount.compareTo(ageLimitChangeDate) >= 0) {
                        baseAmount = baseAmount_releaseDate;
                        if (baseAmount_releaseDate.doubleValue() <= 0) {
                            needConvert = true;
                            //如果是当年入职，则按照【入职日期折算】
                            if (distributionMode == 3&&belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                                calendar = DateUtil.getCalendar(ageLimitChangeDatetemp);
                            }else{
                                calendar = DateUtil.getCalendar(ageLimitChangeDate);
                            }

                            baseAmount = baseAmount_ageLimitChangeDate;
                        } else {
                            if (date4CalcAmount.compareTo(baseAmountChangeDate) >= 0) {
                                baseAmount = baseAmount_ageLimitChangeDate;
                            }
                        }
                    }
                }
            } else if (baseAmountReleaseDate.compareTo(baseAmountChangeDate) <= 0 && baseAmountChangeDate.compareTo(ageLimitChangeDate) < 0) {
                if (date4CalcAmount.compareTo(baseAmountReleaseDate) < 0) {
                    return 0;
                } else if (date4CalcAmount.compareTo(baseAmountReleaseDate) == 0) {
                    baseAmount = baseAmount_releaseDate;
                    if (calcMethod == 0) {
                        needCalc0 = true;
                    }
                } else {
                    if (date4CalcAmount.compareTo(ageLimitChangeDate) < 0) {
                        baseAmount = baseAmount_releaseDate;
                        if (calcMethod == 0) {
                            needCalc0 = true;
                        }
                    } else if (date4CalcAmount.compareTo(ageLimitChangeDate) >= 0) {
                        baseAmount = baseAmount_releaseDate;
                        if (baseAmount_releaseDate.doubleValue() <= 0) {
                            needConvert = true;
                            //如果是当年入职，则按照【入职日期折算】
                            if (distributionMode == 3&&belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                                calendar = DateUtil.getCalendar(ageLimitChangeDatetemp);
                            }else{
                                calendar = DateUtil.getCalendar(ageLimitChangeDate);
                            }

                            baseAmount = baseAmount_ageLimitChangeDate;
                        } else if (calcMethod == 0) {
                            needCalc0 = true;
                        }
                    }
                }
            }

            if (needCalc0) {
                baseAmount = baseAmount_releaseDate;

                Calendar _calendar1 = DateUtil.getCalendar(baseAmountReleaseDate);
                BigDecimal _dayOfYear1 = new BigDecimal("" + (_calendar1.get(Calendar.DAY_OF_YEAR) - 1));
                BigDecimal _actualMaximum = new BigDecimal("" + _calendar1.getActualMaximum(Calendar.DAY_OF_YEAR));

                Calendar _calendar2 = DateUtil.getCalendar(ageLimitChangeDate);
                BigDecimal _dayOfYear2 = new BigDecimal("" + (_calendar2.get(Calendar.DAY_OF_YEAR) - 1));

                baseAmount = (baseAmount_releaseDate.multiply(_dayOfYear2.subtract(_dayOfYear1))
                        .add(baseAmount_ageLimitChangeDate.multiply(_actualMaximum.subtract(_dayOfYear2))))
                        .divide(_actualMaximum.subtract(_dayOfYear1), decimalDigit, RoundingMode.HALF_UP);

                if (convertMode == 0) {
                    if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                        baseAmount = baseAmount_releaseDate.setScale(decimalDigit, RoundingMode.HALF_UP);
                    }
                    if (date4CalcAmount.compareTo(baseAmountChangeDate) >= 0) {
                        baseAmount = baseAmount_ageLimitChangeDate.setScale(decimalDigit, RoundingMode.HALF_UP);
                    }
                } else if (convertMode == 1) {
                    baseAmount = baseAmount;
                } else if (convertMode == 2) {
                    baseAmount = baseAmount.setScale(0, RoundingMode.UP);
                } else if (convertMode == 3) {
                    baseAmount = baseAmount.setScale(0, RoundingMode.DOWN);
                } else if (convertMode == 4) {
                    baseAmount = baseAmount.divide(new BigDecimal("0.5"), 0, RoundingMode.UP).multiply(new BigDecimal("0.5"));
                } else if (convertMode == 5) {
                    baseAmount = baseAmount.divide(new BigDecimal("0.5"), 0, RoundingMode.DOWN).multiply(new BigDecimal("0.5"));
                }
            }

            if (needConvert) {
                //这一年一共有多少天
                BigDecimal actualMaximum = new BigDecimal("" + calendar.getActualMaximum(Calendar.DAY_OF_YEAR));
                //年假变动日期是这一天的第几天
                BigDecimal dayOfYear = new BigDecimal("" + (calendar.get(Calendar.DAY_OF_YEAR) - 1));

                if (convertMode == 0) {
                    baseAmount = baseAmount;
                } else if (convertMode == 1) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, decimalDigit, RoundingMode.HALF_UP);
                } else if (convertMode == 2) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, 0, RoundingMode.UP);
                } else if (convertMode == 3) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, 0, RoundingMode.DOWN);
                } else if (convertMode == 4) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum.multiply(new BigDecimal("0.5")), 0, RoundingMode.UP).multiply(new BigDecimal("0.5"));
                } else if (convertMode == 5) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum.multiply(new BigDecimal("0.5")), 0, RoundingMode.DOWN).multiply(new BigDecimal("0.5"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return baseAmount.setScale(decimalDigit, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * [4-每年发放固定天数]计算假期基数
     *
     * @param params Map<String,Object>
     *               belongYear:所属年份
     *               createDate:人员创建日期
     *               companyStartDate:入职日期
     *               distributionMode:当前假期规则发放方式
     *               annualAmount:当[distributionMode]=[4-每年发放固定天数]时有效
     *               convertMode:折算方式
     *               decimalDigit:折算后的假期余额保留几位小数
     * @return
     */
    private static double getBaseAmountByDis4(Map<String, Object> params) {
        BigDecimal baseAmount = new BigDecimal("0");
        int decimalDigit = 2;
        try {
            //当前日期
            String currentDate = DateUtil.getCurrentDate();
            //当前年份
            String currentYear = currentDate.substring(0, 4);
            //批处理年份
            String belongYear = Util.null2String(params.get("belongYear"));
            //人员的入职日期
            String companyStartDate = Util.null2String(params.get("companyStartDate"));
            //当前假期规则发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue("" + params.get("distributionMode"), 1);
            //当[distributionMode]=[4-每年发放固定天数]时有效
            double annualAmount = Util.getDoubleValue("" + params.get("annualAmount"), 0);
            //折算方式
            int convertMode = Util.getIntValue("" + params.get("convertMode"), 0);
            //折算后的假期余额保留几位小数
            decimalDigit = Util.getIntValue("" + params.get("decimalDigit"), 2);
            //批处理日期（因为存在当前年份批处理上一年份或者批处理下一年份，所以需要做一些特殊处理）
            String date4CalcAmount = "";
            if (belongYear.compareTo(currentYear) < 0) {
                date4CalcAmount = belongYear + "-12-31";
            } else if (belongYear.compareTo(currentYear) == 0) {
                date4CalcAmount = currentDate;
            } else {
                date4CalcAmount = belongYear + "-01-01";
            }
            if (distributionMode == 4) {
                if (date4CalcAmount.compareTo(companyStartDate) < 0) {
                    return 0;
                }
                baseAmount = new BigDecimal("" + annualAmount);
                //当批处理年份=入职年份时，需要折算
                if (!"".equals(companyStartDate) && belongYear.equals(companyStartDate.substring(0, 4))) {
                    //以入职日期来折算
                    Calendar calendar = DateUtil.getCalendar(companyStartDate);
                    //这一年一共有多少天
                    BigDecimal actualMaximum = new BigDecimal("" + calendar.getActualMaximum(Calendar.DAY_OF_YEAR));
                    //年假变动日期是这一天的第几天
                    BigDecimal dayOfYear = new BigDecimal("" + (calendar.get(Calendar.DAY_OF_YEAR) - 1));

                    if (convertMode == 0) {
                        baseAmount = baseAmount;
                    } else if (convertMode == 1) {
                        baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, decimalDigit, RoundingMode.HALF_UP);
                    } else if (convertMode == 2) {
                        baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, 0, RoundingMode.UP);
                    } else if (convertMode == 3) {
                        baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, 0, RoundingMode.DOWN);
                    } else if (convertMode == 4) {
                        baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum.multiply(new BigDecimal("0.5")), 0, RoundingMode.UP).multiply(new BigDecimal("0.5"));
                    } else if (convertMode == 5) {
                        baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum.multiply(new BigDecimal("0.5")), 0, RoundingMode.DOWN).multiply(new BigDecimal("0.5"));
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return baseAmount.setScale(decimalDigit, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * [6-按司龄+工龄自动发放]计算假期基数
     *
     * @param params Map<String,Object>
     *               belongYear:所属年份
     *               createDate:人员创建日期
     *               workStartDate:参加工作日期
     *               companyStartDate:入职日期
     *               rulesDetailId:假期规则的ID
     *               distributionMode:当前假期规则发放方式
     *               legalKey:法定年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               welfareKey:福利年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               calcMethod:假期基数计算方式
     *               convertMode:折算方式
     *               decimalDigit:折算后的假期余额保留几位小数
     * @return
     */
    private static double getBaseAmountByDis6(Map<String, Object> params) {
        //法定年假
        BigDecimal baseAmount = new BigDecimal("0");
        int decimalDigit = 2;
        try {
            //当前日期
            String currentDate = DateUtil.getCurrentDate();
            //当前年份
            String currentYear = currentDate.substring(0, 4);
            //批处理年份
            String belongYear = Util.null2String(params.get("belongYear"));
            //人员的参加工作日期
            String workStartDate = Util.null2String(params.get("workStartDate"));
            //人员的入职日期
            String companyStartDate = Util.null2String(params.get("companyStartDate"));
            //假期规则的ID
            String rulesDetailId = Util.null2String(params.get("rulesDetailId"));
            //当前假期规则发放规则：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue("" + params.get("distributionMode"), 1);
            //法定年假规则(当distributionMode=6时有效)：0-工龄、1-司龄、2-工龄+司龄
            int legalKey = Util.getIntValue("" + params.get("legalKey"), 0);
            //福利年假规则(当distributionMode=6时有效)：0-工龄、1-司龄、2-工龄+司龄
            int welfareKey = Util.getIntValue("" + params.get("welfareKey"), 1);
            //年假基数计算方式：
            // 0-假期基数发放日期和假期基数变动日期均为每年的01月01号(但是假期基数是结合 司龄/工龄 变化前后的值按天数比例折算出来的)、
            // 1-假期基数发放日期和假期基数变动日期均为每年的01月01号、
            // 2-假期基数发放日期发放日期为每年的01月01号，假期基数的变动日期为每年的 入职日期/参加工作日期
            int calcMethod = Util.getIntValue("" + params.get("calcMethod"), 1);
            //假期基数发放日期
            String baseAmountReleaseDate = "";
            //工龄变动日期
            String ageLimitChangeDate = "";
            //司龄变动日期
            String ageLimitChangeDate2 = "";
            //根据假期基数的计算方式得出【假期基数发放日期】、【假期基数变动日期】、【工龄变动日期】、【司龄变动日期】
            if (calcMethod == 0 || calcMethod == 1) {
                baseAmountReleaseDate = belongYear + "-01-01";
                ageLimitChangeDate = belongYear + workStartDate.substring(4);
                ageLimitChangeDate2 = belongYear + companyStartDate.substring(4);
            } else if (calcMethod == 2) {
                baseAmountReleaseDate = belongYear + "-01-01";
                ageLimitChangeDate = belongYear + workStartDate.substring(4);
                ageLimitChangeDate2 = belongYear + companyStartDate.substring(4);
            }
            //批处理日期（因为存在当前年份批处理上一年份或者批处理下一年份，所以需要做一些特殊处理）
            String date4CalcAmount = "";
            if (belongYear.compareTo(currentYear) < 0) {
                date4CalcAmount = belongYear + "-12-31";
            } else if (belongYear.compareTo(currentYear) == 0) {
                date4CalcAmount = currentDate;
            } else {
                date4CalcAmount = baseAmountReleaseDate;
            }
            //获取假期规则的【折算方式】
            int convertMode = Util.getIntValue("" + params.get("convertMode"), 0);
            //获取假期规则的【假期余额小数位数】
            decimalDigit = Util.getIntValue("" + params.get("decimalDigit"), 2);
            //是计算法定年假还是福利年假
            String legalOrWelfare = Util.null2String(params.get("legalOrWelfare"));

            //人员在【假期基数发放日期】时对应的工龄
            int ageLimit_releaseDate = getAgeLimit(workStartDate, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时对应的司龄
            int ageLimit2_releaseDate = getAgeLimit(companyStartDate, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时对应的假期基数
            BigDecimal baseAmount_releaseDate = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_releaseDate, ageLimit2_releaseDate, legalKey, welfareKey, legalOrWelfare);

            //人员在【工龄变动日期】时对应的工龄
            int ageLimit_ageLimitChangeDate = getAgeLimit(workStartDate, ageLimitChangeDate);
            //人员在【工龄变动日期】时对应的司龄
            int ageLimit2_ageLimitChangeDate = getAgeLimit(companyStartDate, ageLimitChangeDate);
            //人员在【工龄变动日期】时对应的假期基数
            BigDecimal baseAmount_ageLimitChangeDate = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_ageLimitChangeDate, ageLimit2_ageLimitChangeDate, legalKey, welfareKey, legalOrWelfare);

            //人员在【司龄变动日期】时对应的司龄
            int ageLimit_ageLimitChangeDate2 = getAgeLimit(workStartDate, ageLimitChangeDate2);
            //人员在【司龄变动日期】时对应的司龄
            int ageLimit2_ageLimitChangeDate2 = getAgeLimit(companyStartDate, ageLimitChangeDate2);
            //人员在【司龄变动日期】时对应的假期基数
            BigDecimal baseAmount_ageLimitChangeDate2 = getAmountByAgeLimit(distributionMode, rulesDetailId, -1, ageLimit_ageLimitChangeDate2, ageLimit2_ageLimitChangeDate2, legalKey, welfareKey, legalOrWelfare);

            //是否需要精确计算
            boolean needCalc0 = false;
            //是否需要折算
            boolean needConvert = false;
            //根据什么日期进行折算
            Calendar calendar = null;
            //如果【批处理日期】小于【入职日期】，假期基数应该为0
            if (date4CalcAmount.compareTo(companyStartDate) < 0) {
                return 0;
            }
            //如果批处理年份等于入职年份，那应该以【入职日期】来折算
            String ageLimitChangeDatetemp = "";
            if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                //需要折算
                needConvert = true;
                //以【入职日期来折算】
                calendar = DateUtil.getCalendar(companyStartDate);
                //如果是初始获得年，那么，入职日期和参加工作日期，取相对较晚的日期，作为变动日期
                if(companyStartDate.substring(4).compareTo(workStartDate.substring(4)) <= 0){
                    ageLimitChangeDatetemp = belongYear + workStartDate.substring(4);
                }else{
                    ageLimitChangeDatetemp = belongYear + companyStartDate.substring(4);
                }
            }
            for (int i = 0; i < 1; i++) {
                //如果【计算假期基数的日期】小于【假期基数发放日期】，假期基数应该为0
                if (date4CalcAmount.compareTo(baseAmountReleaseDate) < 0) {
                    return 0;
                } else if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                    baseAmount = baseAmount_releaseDate;
                    //暂定【假期基数发放日期】一定小于等于【工龄变动日期】、【司龄变动日期】
                    //【假期基数发放日期】<=【工龄变动日期】<=【司龄变动日期】
                    if (baseAmountReleaseDate.compareTo(ageLimitChangeDate) <= 0 && ageLimitChangeDate.compareTo(ageLimitChangeDate2) <= 0) {
                        if (calcMethod == 0 && baseAmount_releaseDate.doubleValue() > 0) {
                            Calendar _calendar1 = DateUtil.getCalendar(baseAmountReleaseDate);
                            BigDecimal _dayOfYear1 = new BigDecimal("" + (_calendar1.get(Calendar.DAY_OF_YEAR) - 1));
                            BigDecimal _actualMaximum = new BigDecimal("" + (_calendar1.getActualMaximum(Calendar.DAY_OF_YEAR)));

                            Calendar _calender2 = DateUtil.getCalendar(ageLimitChangeDate);
                            BigDecimal _dayOfYear2 = new BigDecimal("" + (_calender2.get(Calendar.DAY_OF_YEAR) - 1));

                            Calendar _calender3 = DateUtil.getCalendar(ageLimitChangeDate2);
                            BigDecimal _dayOfYear3 = new BigDecimal("" + (_calender3.get(Calendar.DAY_OF_YEAR) - 1));

                            baseAmount = ((baseAmount_releaseDate.multiply(_dayOfYear2.subtract(_dayOfYear1)))
                                    .add(baseAmount_ageLimitChangeDate.multiply(_dayOfYear3.subtract(_dayOfYear2)))
                                    .add(baseAmount_ageLimitChangeDate2.multiply(_actualMaximum.subtract(_dayOfYear3))))
                                    .divide(_actualMaximum, decimalDigit, RoundingMode.HALF_UP);
                            if (convertMode == 0) {
                                if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                                    baseAmount = baseAmount_releaseDate.setScale(decimalDigit, RoundingMode.HALF_UP);
                                }
                                if (date4CalcAmount.compareTo(ageLimitChangeDate) >= 0) {
                                    baseAmount = baseAmount_ageLimitChangeDate.setScale(decimalDigit, RoundingMode.HALF_UP);
                                }
                                if (date4CalcAmount.compareTo(ageLimitChangeDate2) >= 0) {
                                    baseAmount = baseAmount_ageLimitChangeDate2.setScale(decimalDigit, RoundingMode.HALF_UP);
                                }
                            } else if (convertMode == 1) {
                                baseAmount = baseAmount;
                            } else if (convertMode == 2) {
                                baseAmount = baseAmount.setScale(0, RoundingMode.UP);
                            } else if (convertMode == 3) {
                                baseAmount = baseAmount.setScale(0, RoundingMode.DOWN);
                            } else if (convertMode == 4) {
                                baseAmount = baseAmount.divide(new BigDecimal("0.5"), 0, RoundingMode.UP).multiply(new BigDecimal("0.5"));
                            } else if (convertMode == 5) {
                                baseAmount = baseAmount.divide(new BigDecimal("0.5"), 0, RoundingMode.DOWN).multiply(new BigDecimal("0.5"));
                            }
                            break;
                        }
                        //【批处理日期】>=【工龄变动日期】&& (人员在【假期基数发放日期】时对应的假期基数是0 || 【假期基数计算方式=2】时)
                        if ((date4CalcAmount.compareTo(ageLimitChangeDate) >= 0)
                                && (baseAmount_releaseDate.doubleValue() <= 0 || calcMethod == 2)) {
                            if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate.doubleValue() > 0) {
                                needConvert = true;
                                //如果是当年入职，则按照【入职日期折算】
                                if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                                    calendar = DateUtil.getCalendar(ageLimitChangeDatetemp);
                                }else{
                                    calendar = DateUtil.getCalendar(ageLimitChangeDate);
                                }

                                baseAmount = baseAmount_ageLimitChangeDate;
                            } else {
                                if (calcMethod == 2) {
                                    baseAmount = baseAmount_ageLimitChangeDate;
                                }
                            }
                        }
                        //【批处理日期】>=【司龄变动日期】&& (人员在【工龄变动日期】时对应的假期基数是0||【假期基数计算方式=2】时)
                        if ((date4CalcAmount.compareTo(ageLimitChangeDate2) >= 0)
                                && (baseAmount_ageLimitChangeDate.doubleValue() <= 0 || calcMethod == 2)) {
                            if (baseAmount_ageLimitChangeDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate2.doubleValue() > 0) {
                                needConvert = true;
                                if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                                    calendar = DateUtil.getCalendar(ageLimitChangeDatetemp);
                                }else{
                                    calendar = DateUtil.getCalendar(ageLimitChangeDate2);
                                }
                                baseAmount = baseAmount_ageLimitChangeDate2;
                            } else {
                                if (calcMethod == 2) {
                                    baseAmount = baseAmount_ageLimitChangeDate2;
                                }
                            }
                        }
                    } else if (baseAmountReleaseDate.compareTo(ageLimitChangeDate2) <= 0 && ageLimitChangeDate2.compareTo(ageLimitChangeDate) <= 0) {
                        if (calcMethod == 0 && baseAmount_releaseDate.doubleValue() > 0) {
                            Calendar _calendar1 = DateUtil.getCalendar(baseAmountReleaseDate);
                            BigDecimal _dayOfYear1 = new BigDecimal("" + (_calendar1.get(Calendar.DAY_OF_YEAR) - 1));
                            BigDecimal _actualMaximum = new BigDecimal("" + _calendar1.getActualMaximum(Calendar.DAY_OF_YEAR));

                            Calendar _calender2 = DateUtil.getCalendar(ageLimitChangeDate2);
                            BigDecimal _dayOfYear2 = new BigDecimal("" + (_calender2.get(Calendar.DAY_OF_YEAR) - 1));

                            Calendar _calender3 = DateUtil.getCalendar(ageLimitChangeDate);
                            BigDecimal _dayOfYear3 = new BigDecimal("" + (_calender3.get(Calendar.DAY_OF_YEAR) - 1));

                            baseAmount = ((baseAmount_releaseDate.multiply(_dayOfYear2.subtract(_dayOfYear1)))
                                    .add(baseAmount_ageLimitChangeDate2.multiply(_dayOfYear3.subtract(_dayOfYear2)))
                                    .add(baseAmount_ageLimitChangeDate.multiply(_actualMaximum.subtract(_dayOfYear3))))
                                    .divide(_actualMaximum, decimalDigit, RoundingMode.HALF_UP);
                            if (convertMode == 0) {
                                if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                                    baseAmount = baseAmount_releaseDate.setScale(decimalDigit, RoundingMode.HALF_UP);
                                }
                                if (date4CalcAmount.compareTo(ageLimitChangeDate2) >= 0) {
                                    baseAmount = baseAmount_ageLimitChangeDate2.setScale(decimalDigit, RoundingMode.HALF_UP);
                                }
                                if (date4CalcAmount.compareTo(ageLimitChangeDate) >= 0) {
                                    baseAmount = baseAmount_ageLimitChangeDate.setScale(decimalDigit, RoundingMode.HALF_UP);
                                }
                            } else if (convertMode == 1) {
                                baseAmount = baseAmount;
                            } else if (convertMode == 2) {
                                baseAmount = baseAmount.setScale(0, RoundingMode.UP);
                            } else if (convertMode == 3) {
                                baseAmount = baseAmount.setScale(0, RoundingMode.DOWN);
                            } else if (convertMode == 4) {
                                baseAmount = baseAmount.divide(new BigDecimal("0.5"), 0, RoundingMode.UP).multiply(new BigDecimal("0.5"));
                            } else if (convertMode == 5) {
                                baseAmount = baseAmount.divide(new BigDecimal("0.5"), 0, RoundingMode.DOWN).multiply(new BigDecimal("0.5"));
                            }
                            break;
                        }
                        if ((date4CalcAmount.compareTo(ageLimitChangeDate2) >= 0)
                                && (baseAmount_releaseDate.doubleValue() <= 0 || calcMethod == 2)) {
                            if (baseAmount_releaseDate.doubleValue() <= 0 && baseAmount_ageLimitChangeDate2.doubleValue() > 0) {
                                needConvert = true;
                                if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                                    calendar = DateUtil.getCalendar(ageLimitChangeDatetemp);
                                }else{
                                    calendar = DateUtil.getCalendar(ageLimitChangeDate2);
                                }

                                baseAmount = baseAmount_ageLimitChangeDate2;
                            } else {
                                if (calcMethod == 2) {
                                    baseAmount = baseAmount_ageLimitChangeDate2;
                                }
                            }
                        }
                        if ((date4CalcAmount.compareTo(ageLimitChangeDate) >= 0)
                                && (baseAmount_ageLimitChangeDate2.doubleValue() <= 0 || calcMethod == 2)) {
                            if (baseAmount_ageLimitChangeDate2.doubleValue() <= 0 && baseAmount_ageLimitChangeDate.doubleValue() > 0) {
                                needConvert = true;
                                if (belongYear.compareTo(companyStartDate.substring(0, 4)) == 0) {
                                    calendar = DateUtil.getCalendar(ageLimitChangeDatetemp);
                                }else{
                                    calendar = DateUtil.getCalendar(ageLimitChangeDate);
                                }

                                baseAmount = baseAmount_ageLimitChangeDate;
                            } else {
                                if (calcMethod == 2) {
                                    baseAmount = baseAmount_ageLimitChangeDate;
                                }
                            }
                        }
                    }
                }
            }

            if (needConvert) {
                //这一年一共有多少天
                BigDecimal actualMaximum = new BigDecimal("" + calendar.getActualMaximum(Calendar.DAY_OF_YEAR));
                //年假变动日期是这一天的第几天
                BigDecimal dayOfYear = new BigDecimal("" + (calendar.get(Calendar.DAY_OF_YEAR) - 1));

                if (convertMode == 0) {
                    baseAmount = baseAmount;
                } else if (convertMode == 1) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, decimalDigit, RoundingMode.HALF_UP);
                } else if (convertMode == 2) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, 0, RoundingMode.UP);
                } else if (convertMode == 3) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum, 0, RoundingMode.DOWN);
                } else if (convertMode == 4) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum.multiply(new BigDecimal("0.5")), 0, RoundingMode.UP).multiply(new BigDecimal("0.5"));
                } else if (convertMode == 5) {
                    baseAmount = baseAmount.multiply(actualMaximum.subtract(dayOfYear)).divide(actualMaximum.multiply(new BigDecimal("0.5")), 0, RoundingMode.DOWN).multiply(new BigDecimal("0.5"));
                }
            } else {
                baseAmount = baseAmount;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return baseAmount.setScale(decimalDigit, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * [8-育儿假]计算假期基数
     *
     * @param params Map<String,Object>
     *               belongYear:所属年份
     *               createDate:人员创建日期
     *               workStartDate:参加工作日期
     *               companyStartDate:入职日期
     *               rulesDetailId:假期规则的ID
     *               distributionMode:当前假期规则发放方式
     *               annualAmount:当[distributionMode]=[4-每年发放固定天数]时有效
     *               legalKey:法定年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               welfareKey:福利年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               calcMethod:假期基数计算方式
     *               convertMode:折算方式
     *               decimalDigit:折算后的假期余额保留几位小数
     * @return
     */
    private static Map<String, Object> getBaseAmountByDis8(Map<String, Object> params) {
        Map<String,Object> returnMap = Maps.newHashMap();

        try{
            String resourceId = Util.null2String(params.get("resourceId"));
            ArrayList<String> listDates = getParentalLeaveDate(resourceId);
            //当前日期
            String currentDate = DateUtil.getCurrentDate();
            //当前年份
            String currentYear = currentDate.substring(0, 4);
            //批处理年份
            String belongYear = Util.null2String(params.get("belongYear"));
            String belongYearNew = "";
            //当[distributionMode]=[8-每年发放固定天数]时有效
            double annualAmount = Util.getDoubleValue("" + params.get("annualAmount"), 0);

            BigDecimal baseAmount = new BigDecimal("0");
            int decimalDigit = 2;
            boolean addOrUpdate = false;
            String expirationDate = "";
            String expirationDateNew = currentDate;

            //折算后的假期余额保留几位小数
            decimalDigit = Util.getIntValue("" + params.get("decimalDigit"), 2);
            //批处理日期（因为存在当前年份批处理上一年份或者批处理下一年份，所以需要做一些特殊处理）
            String date4CalcAmount = currentDate;

            /*
             * 1、考虑1个孩子，0年、1年、2年发放
             * 2、考虑2个孩子，0年、1年、2年发放，第一个孩子有效期到期了，才考虑第二个孩子的。如果当前日期在第二个孩子有效期内，那么就存到有效期对应的当前年份
             *第二个孩子的2021年1月1号
             * */
            for (int i = 0; i < listDates.size(); i++) {
                String dateOfBirth=listDates.get(i);
                String yearOfBirth=dateOfBirth.substring(0,4);

                String baseAmountReleaseDate = belongYear + dateOfBirth.substring(4);
                int ageLimit = getAgeLimit(dateOfBirth, baseAmountReleaseDate);
                if(ageLimit>=0&&ageLimit<=2){
                    baseAmount = new BigDecimal("" + annualAmount);
                }else{
                    baseAmount = new BigDecimal("0");
                }
//第二个孩子的情况
                if(!expirationDate.equals("")&& belongYear.compareTo(expirationDate.substring(0, 4)) == 0){
                    if(baseAmountReleaseDate.compareTo(expirationDate) > 0){
                        if(date4CalcAmount.compareTo(expirationDate) > 0&&date4CalcAmount.compareTo(baseAmountReleaseDate) < 0){
                            belongYearNew = (Util.getIntValue(belongYear) - 1) + "";
                            addOrUpdate = true;
                            expirationDateNew = date4CalcAmount;
                            continue;
                        }else if(date4CalcAmount.compareTo(expirationDate) <= 0){
                            baseAmount = new BigDecimal("0");
                            continue;
                        }
                    }else{
                        if(date4CalcAmount.compareTo(expirationDate) > 0){
                            break;
                        }else{
                            baseAmount = new BigDecimal("0");
                            continue;
                        }
                    }
                }

                expirationDate = (Util.getIntValue(yearOfBirth) + 3) + dateOfBirth.substring(4);

                if(ageLimit>=0&&ageLimit<=2){
                }else{
                    baseAmount = new BigDecimal("0");
                    continue;
                }

                //批处理日期>=假期基数释放日期--出生日期
                if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                } else {
                    baseAmount = new BigDecimal("0");
                }
                break;
            }


            returnMap.put("baseAmount", baseAmount.setScale(decimalDigit, RoundingMode.HALF_UP).doubleValue());
            returnMap.put("addOrUpdate",addOrUpdate);
            returnMap.put("expirationDate",expirationDateNew);
            returnMap.put("belongYearNew",belongYearNew);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return returnMap;
    }


    /**
     * [7-按司龄自动发放(入职日期当天发放假期余额)]计算假期基数
     *
     * @param params Map<String,Object>
     *               belongYear:所属年份
     *               createDate:人员创建日期
     *               workStartDate:参加工作日期
     *               companyStartDate:入职日期
     *               rulesDetailId:假期规则的ID
     *               distributionMode:当前假期规则发放方式
     *               annualAmount:当[distributionMode]=[4-每年发放固定天数]时有效
     *               legalKey:法定年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               welfareKey:福利年假规则(当[distributionMode=6]时有效)：0-工龄、1-司龄、2-工龄+司龄
     *               calcMethod:假期基数计算方式
     *               convertMode:折算方式
     *               decimalDigit:折算后的假期余额保留几位小数
     * @return
     */
    private static double getBaseAmountByDis7(Map<String, Object> params) {
        double baseAmount = 0;
        try{
            //当前日期
            String currentDate = DateUtil.getCurrentDate();
            //当前年份
            String currentYear = currentDate.substring(0, 4);
            //批处理年份
            String belongYear = Util.null2String(params.get("belongYear"));
            //人员的参加工作日期
            String workStartDate = Util.null2String(params.get("workStartDate"));
            //人员的入职日期
            String companyStartDate = Util.null2String(params.get("companyStartDate"));

            //假期规则的ID
            String rulesDetailId = Util.null2String(params.get("rulesDetailId"));
            //当前假期规则发放方式：1-手动发放、2-按司龄自动发放、3-按工龄自动发放、4-每年自动发放固定天数、5-加班时长自动计入余额、6-按入职时长+工龄自动发放
            int distributionMode = Util.getIntValue("" + params.get("distributionMode"), 1);
            //假期基数发放日期
            String baseAmountReleaseDate = belongYear + companyStartDate.substring(4);
            //默认保留两位小数
            int decimalDigit = Util.getIntValue("" + params.get("decimalDigit"), 2);

            if (distributionMode != 7) {
                return baseAmount;
            }

            //批处理日期
            String date4CalcAmount = currentDate;

            //人员在【假期基数发放日期】时对应的【司龄】
            int ageLimit = getAgeLimit(companyStartDate, baseAmountReleaseDate);
            //人员在【假期基数发放日期】时应有的【假期基数】
            BigDecimal baseAmount_releaseDate = getAmountByAgeLimit(distributionMode, rulesDetailId, 0, ageLimit, -1, -1, -1, "");

            //批处理日期>=假期基数释放日期
            if (date4CalcAmount.compareTo(baseAmountReleaseDate) >= 0) {
                baseAmount = baseAmount_releaseDate.setScale(2, RoundingMode.HALF_UP).doubleValue();
            } else {
                baseAmount = 0;
            }
        }catch (Exception e){
            e.printStackTrace();
        }
        return baseAmount;
    }

    /**
     * 获取调休的余额
     *
     * @param ruleId            假期类型的ID
     * @param resourceId        人员ID
     * @param searchDate        计算是否失效的指定日期
     * @param calcByCurrentDate 是根据当前日期计算还是根据指定日期计算假期是否失效：true-当前日期、false-指定日期
     * @param isAll             是获取所有年份的还是获取指定年份的：true-所有年份的、false-指定年份的
     * @return
     */
    private static BigDecimal getRestAmountByDis5(String ruleId, String resourceId, String searchDate, boolean calcByCurrentDate, boolean isAll) {
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
            //qc2474646加个判断，获取调休余额为查询指定日期当月余额
//            if (!"1".equals(isFromMyAttendance)){
                date = date.substring(5,7);     //划出月份
                String quarter = getCurrentQuarter(Integer.parseInt(date));
                sql += " and belongMonth IN " + quarter + " and expirationDate>='" +currentDate+"'";
//            }
           /* date = date.substring(5,7);     //划出月份
            int[] quarter = getCurrentQuarter(Integer.parseInt(date));
            int startMonth = quarter[0];
            int endMonth = quarter[1];
            sql += " and belongMonth BETWEEN '" + startMonth +"' AND '" + endMonth + "'" + " and expirationDate>='" +currentDate+"'";*/
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
     * 获取调休的余额
     *
     * @param params
     * @param user
     * @return
     */
    public static Map<String, BigDecimal> getRestAmountMapByDis5(Map<String, Object> params, User user) {
        Map<String, BigDecimal> balanceMap = new HashMap<String, BigDecimal>();
        try {
            /**
             * 分页控件返回的值
             * currentPage：当前页数
             * pageSize：每页多少条数据
             * showAll：是显示分页还是显示所有
             */
            int currentPage = Util.getIntValue((String) params.get("currentPage"), 1);
            int pageSize = Util.getIntValue((String) params.get("pageSize"), 10);
            boolean showAll = Util.null2s((String) params.get("showAll"), "true").equals("true");
            /**
             * 时间范围选择的年份
             * dateScope：5-本年、8-上一年
             * selectedYear：指定年份
             */
            String dateScope = Util.null2String(params.get("dateScope"));
            String selectedYear = Util.null2String(params.get("selectedYear"));
            if (dateScope.equals("5") || dateScope.equals("8")) {
                selectedYear = TimeUtil.getDateByOption(dateScope, "0").substring(0, 4);
            }
            /**
             * 数据范围
             * dataScope：0-总部、1-分部、2-分部、3-人员、4-我的下属
             * subcomId：指定分部ID
             * deptId：指定部门ID
             * resourceId：指定人员ID
             * allLevel：是否包含下级下属：0-不包含、1-包含
             */
            String dataScope = Util.null2String(params.get("dataScope"));
            String subcomId = Util.null2String(params.get("subcomId"));
            String deptId = Util.null2String(params.get("deptId"));
            String resourceId = Util.null2String(params.get("resourceId"));
            String allLevel = Util.null2String(params.get("allLevel"));
            //人员状态
            String resourceStatus = Util.null2String(params.get("status"));

            /**
             * isNoAccount：是否显示无账号人员：true-显示、false-不显示
             */
            String isNoAccount = Util.null2String(params.get("isNoAccount"));
            /**
             * 假期类型的ID
             */
            String leaveRulesId = Util.null2String(params.get("leaveRulesId"));
            /**
             * 当前日期
             */
            String currentDate = DateUtil.getCurrentDate();
            /**
             * 获取考勤报表权限共享设置
             */
            KQReportBiz kqReportBiz = new KQReportBiz();
            String rightStr = kqReportBiz.getReportRight("4", "" + user.getUID(), "a");

            RecordSet recordSet = new RecordSet();
            String sql = "select a.id hrmResourceId,b.* from HrmResource a left join ";
            if (recordSet.getDBType().equalsIgnoreCase("sqlserver")) {
                sql = "select a.id hrmResourceId,b.*,ROW_NUMBER() OVER(order by dspOrder,a.id) as rn from HrmResource a left join ";
            }
            if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                    || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                sql += "(select resourceId, sum(baseAmount) as allBaseAmount, sum(tiaoxiuamount) as allTiaoxiuamount,sum(extraAmount) as allExtraAmount, sum(usedAmount) as allUsedAmount " +
                        "from kq_balanceOfLeave " +
                        "where 1=1 and (isDelete is null or isDelete<>1) and (expirationDate is null or expirationDate='' or expirationDate >= '" + currentDate + "') and belongYear='" + selectedYear + "' and leaveRulesId=" + leaveRulesId + " " +
                        "group by resourceId) b " +
                        "on a.id = b.resourceId where 1=1 ";
            } else {
                sql += "(select resourceId, sum(baseAmount) as allBaseAmount, sum(tiaoxiuamount) as allTiaoxiuamount,sum(extraAmount) as allExtraAmount, sum(usedAmount) as allUsedAmount " +
                        "from kq_balanceOfLeave " +
                        "where 1=1 and (isDelete is null or isDelete<>1) and (expirationDate is null or expirationDate >= '" + currentDate + "') and belongYear='" + selectedYear + "' and leaveRulesId=" + leaveRulesId + " " +
                        "group by resourceId) b " +
                        "on a.id = b.resourceId where 1=1 ";
            }
            if (dataScope.equals("0")) {
                //总部
            } else if (dataScope.equals("1")) {
                sql += " and a.subcompanyId1 in (" + subcomId + ") ";
            } else if (dataScope.equals("2")) {
                sql += " and a.departmentId in (" + deptId + ") ";
            } else if (dataScope.equals("3")) {
                sql += " and a.id in (" + resourceId + ")";
            } else if (dataScope.equals("4")) {
                if (allLevel.equals("1")) {
                    sql += " and (a.id=" + user.getUID() + " or a.managerStr like '%," + user.getUID() + ",%' )";
                } else {
                    sql += " and (a.id=" + user.getUID() + " or a.managerid = " + user.getUID() + ")";
                }
            }

            if(resourceStatus.length()>0){
                if (!resourceStatus.equals("8") && !resourceStatus.equals("9")) {
                    sql += " and a.status = "+resourceStatus+ "";
                }else if (resourceStatus.equals("8")) {
                    sql += " and (a.status = 0 or a.status = 1 or a.status = 2 or a.status = 3) ";
                }
            }

            if (isNoAccount.equals("false")) {
                if (recordSet.getDBType().equalsIgnoreCase("sqlserver")
                        || recordSet.getDBType().equalsIgnoreCase("mysql")) {
                    sql += " and (loginId is not null and loginId<>'')";
                } else {
                    sql += " and (loginId is not null)";
                }
            }
            //考勤报表共享设置
            if (!rightStr.equals("") && !dataScope.equals("4")) {
                sql += rightStr;
            }
            if (!recordSet.getDBType().equalsIgnoreCase("sqlserver")) {
                sql += " order by dspOrder,hrmResourceId ";
            }
            if (!showAll) {
                String pageSql = "select * from (select tmp.*,rownum rn from (" + sql + ") tmp where rownum<=" + (pageSize * currentPage) + ") where rn>=" + (pageSize * (currentPage - 1) + 1);
                if (recordSet.getDBType().equalsIgnoreCase("sqlserver")) {
                    pageSql = "select t.* from (" + sql + ") t where 1=1 and rn>=" + (pageSize * (currentPage - 1) + 1) + " and rn<=" + (pageSize * currentPage);
                } else if (recordSet.getDBType().equalsIgnoreCase("mysql")) {
                    pageSql = sql + " limit " + (currentPage - 1) * pageSize + "," + pageSize;
                }else if (recordSet.getDBType().equalsIgnoreCase("postgresql")) {
                    pageSql = sql + " limit " + pageSize + " offset " + (currentPage - 1) * pageSize;
                }
                recordSet.executeQuery(pageSql);
            } else {
                recordSet.executeQuery(sql);
            }
            while (recordSet.next()) {
                String hrmResourceId = Util.null2String(recordSet.getString("hrmResourceId"));
                BigDecimal allBaseAmount = new BigDecimal(Util.null2s(recordSet.getString("allBaseAmount"), "0"));
                BigDecimal allExtraAmount = new BigDecimal(Util.null2s(recordSet.getString("allExtraAmount"), "0"));
                BigDecimal allUsedAmount = new BigDecimal(Util.null2s(recordSet.getString("allUsedAmount"), "0"));
                BigDecimal allTiaoxiuamount = new BigDecimal(Util.null2s(recordSet.getString("allTiaoxiuamount"), "0"));

                balanceMap.put(hrmResourceId + "_" + leaveRulesId, allBaseAmount.add(allExtraAmount).add(allTiaoxiuamount).subtract(allUsedAmount));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return balanceMap;
    }

    /**
     * 插入或者更新员工假期余额
     *
     * @param newBalanceEntities 新假期余额
     * @param oldBalanceEntities 旧假期余额
     * @param operator           操作者
     * @param canUpdate          是否允许更新
     * @return
     */
    private static boolean insertOrUpdateBalance(List<KQBalanceOfLeaveEntity> newBalanceEntities, Map<String, Object> oldBalanceEntities, String operator, boolean canUpdate) {
        /*获取今天的日期、此刻的时间*/
        Calendar today = Calendar.getInstance();
        String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-"
                + Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-"
                + Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);
        String currentTime = Util.add0(today.get(Calendar.HOUR_OF_DAY), 2) + ":" +
                Util.add0(today.get(Calendar.MINUTE), 2) + ":" +
                Util.add0(today.get(Calendar.SECOND), 2);

        String insertSql = "insert into kq_balanceOfLeave(leaveRulesId,resourceId,belongYear,baseAmount,extraAmount,usedAmount,baseAmount2,extraAmount2,usedAmount2) " +
                "values(?,?,?,?,?,?,?,?,?)";
        List insertList = new ArrayList();
        List insertParamList = new ArrayList();
        String updateSql = "update kq_balanceOfLeave set leaveRulesId=?,resourceId=?,belongYear=?,baseAmount=?,baseAmount2=? where id=?";
        String updateSql2 ="update kq_balanceOfLeave set leaveRulesId=?,resourceId=?,belongYear=?,baseAmount=?,baseAmount2=?,usedAmount=?,extraAmount=? where id=?";

        List updateList = new ArrayList();
        List updateParamList = new ArrayList();
        List updateParamList2 = new ArrayList();
        List<KQUsageHistoryEntity> usageHistoryEntities = new ArrayList<KQUsageHistoryEntity>();
        KQUsageHistoryEntity usageHistoryEntity = null;
        for (KQBalanceOfLeaveEntity newBalanceEntity : newBalanceEntities) {
            int resourceId = newBalanceEntity.getResourceId();
            int status = newBalanceEntity.getStatus();
            KQBalanceOfLeaveEntity oldBalanceEntity = (KQBalanceOfLeaveEntity) oldBalanceEntities.get("" + resourceId);
            if (status == 1) {
                String belongYear = newBalanceEntity.getBelongYear();
                String ruleId = newBalanceEntity.getLeaveRulesId() + "";
                Map<String, Object> oldBalanceEntitiesLast = getBalanceEntities(3, resourceId + "", ruleId, belongYear);
                oldBalanceEntity = (KQBalanceOfLeaveEntity) oldBalanceEntitiesLast.get("" + resourceId);
            }
            if (oldBalanceEntity == null || oldBalanceEntity.getId() <= 0) {
                insertList = new ArrayList();
                insertList.add(newBalanceEntity.getLeaveRulesId());
                insertList.add(newBalanceEntity.getResourceId());
                insertList.add(newBalanceEntity.getBelongYear());
                insertList.add(newBalanceEntity.getBaseAmount());
                insertList.add(newBalanceEntity.getExtraAmount());
                insertList.add(newBalanceEntity.getUsedAmount());
                insertList.add(newBalanceEntity.getBaseAmount2());
                insertList.add(newBalanceEntity.getExtraAmount2());
                insertList.add(newBalanceEntity.getUsedAmount2());
                insertParamList.add(insertList);

                usageHistoryEntity = new KQUsageHistoryEntity();
                usageHistoryEntity.setLeaveRulesId("" + newBalanceEntity.getLeaveRulesId());
                usageHistoryEntity.setRelatedId("" + newBalanceEntity.getResourceId());
                usageHistoryEntity.setOperator("" + operator);
                usageHistoryEntity.setOperateDate(currentDate);
                usageHistoryEntity.setOperateTime(currentTime);
                usageHistoryEntity.setOperateType("6");
                usageHistoryEntity.setBelongYear(newBalanceEntity.getBelongYear());
                usageHistoryEntity.setNewBaseAmount("" + newBalanceEntity.getBaseAmount());
                usageHistoryEntity.setNewExtraAmount("" + newBalanceEntity.getExtraAmount());
                usageHistoryEntity.setNewUsedAmount("" + newBalanceEntity.getUsedAmount());
                usageHistoryEntity.setNewBaseAmount2("" + newBalanceEntity.getBaseAmount2());
                usageHistoryEntity.setNewExtraAmount2("" + newBalanceEntity.getExtraAmount2());
                usageHistoryEntity.setNewUsedAmount2("" + newBalanceEntity.getUsedAmount2());
                usageHistoryEntity.setOldMinimumUnit("" + newBalanceEntity.getMinimumUnit());
                usageHistoryEntity.setNewMinimumUnit("" + newBalanceEntity.getMinimumUnit());
                usageHistoryEntity.setInsertOrUpdate("insert");
                usageHistoryEntities.add(usageHistoryEntity);
            } else {
                updateList = new ArrayList();
                updateList.add(newBalanceEntity.getLeaveRulesId());
                updateList.add(newBalanceEntity.getResourceId());
                updateList.add(newBalanceEntity.getBelongYear());
                updateList.add(newBalanceEntity.getBaseAmount());
                updateList.add(newBalanceEntity.getBaseAmount2());
                if(status==1){
                    updateList.add(newBalanceEntity.getUsedAmount());
                    updateList.add(newBalanceEntity.getExtraAmount());
                }
                updateList.add(oldBalanceEntity.getId());
                if(status==1){
                    updateParamList2.add(updateList);
                }else{
                    updateParamList.add(updateList);
                }

                usageHistoryEntity = new KQUsageHistoryEntity();
                usageHistoryEntity.setLeaveRulesId("" + newBalanceEntity.getLeaveRulesId());
                usageHistoryEntity.setRelatedId("" + newBalanceEntity.getResourceId());
                usageHistoryEntity.setOperator("" + operator);
                usageHistoryEntity.setOperateDate(currentDate);
                usageHistoryEntity.setOperateTime(currentTime);
                usageHistoryEntity.setOperateType("6");
                usageHistoryEntity.setBelongYear(newBalanceEntity.getBelongYear());
                usageHistoryEntity.setOldBaseAmount("" + oldBalanceEntity.getBaseAmount());
                usageHistoryEntity.setNewBaseAmount("" + newBalanceEntity.getBaseAmount());
                if(status==1){
                    usageHistoryEntity.setOldUsedAmount("" + oldBalanceEntity.getUsedAmount());
                    usageHistoryEntity.setNewUsedAmount("" + newBalanceEntity.getUsedAmount());
                    usageHistoryEntity.setOldExtraAmount("" + oldBalanceEntity.getExtraAmount());
                    usageHistoryEntity.setNewExtraAmount("" + newBalanceEntity.getExtraAmount());
                }
                usageHistoryEntity.setOldBaseAmount2("" + oldBalanceEntity.getBaseAmount2());
                usageHistoryEntity.setNewBaseAmount2("" + newBalanceEntity.getBaseAmount2());
                usageHistoryEntity.setOldMinimumUnit("" + oldBalanceEntity.getMinimumUnit());
                usageHistoryEntity.setNewMinimumUnit("" + newBalanceEntity.getMinimumUnit());
                usageHistoryEntity.setInsertOrUpdate("update");
                usageHistoryEntities.add(usageHistoryEntity);
            }
        }

        boolean isSuccess = true;
        RecordSet recordSet = new RecordSet();
        /*新增员工假期余额数据 start*/
        if (insertParamList.size() > 0) {
            isSuccess = recordSet.executeBatchSql(insertSql, insertParamList);
        }
        /*新增员工假期余额数据 end*/

        /*更新员工假期余额数据 start*/
        if (updateParamList.size() > 0) {
            isSuccess = recordSet.executeBatchSql(updateSql, updateParamList);
        }
        if (updateParamList2.size() > 0) {
            isSuccess = recordSet.executeBatchSql(updateSql2, updateParamList2);
        }
        /*更新员工假期余额数据 end*/

        /*记录员工假期余额变更记录 start*/
        KQUsageHistoryBiz kqUsageHistoryBiz = new KQUsageHistoryBiz();
        if (usageHistoryEntities.size() > 0) {
            isSuccess = kqUsageHistoryBiz.save(usageHistoryEntities);
        }
        /*记录员工假期余额变更记录 end*/

        return isSuccess;
    }

    /**
     * 根据[工龄]、[司龄]获取对应的假期基数
     *
     * @param distributionMode [发放方式]
     * @param rulesDetailId    [假期规则的ID]
     * @param annualAmount     [发放方式]=[4-每年发放固定天数]时的[固定天数]
     * @param ageLimit         [工龄]or[司龄]、当[发放方式]=[6-按司龄+工龄自动发放]时的[工龄]
     * @param ageLimit2        当[发放方式]=[6-按司龄+工龄自动发放]时的[司龄]
     * @param legalKey         [法定年假规则]：当[发放方式]=[6-按司龄+工龄自动发放]时有效
     * @param welfareKey       [福利年假规则]：当[发放方式]=[6-按司龄+工龄自动发放]时有效
     * @param legalOrWelfare   是计算[法定年假]还是计算[福利年假]：legal-[法定年假]、welfare-[福利年假]
     * @return [假期基数]
     */
    private static BigDecimal getAmountByAgeLimit(int distributionMode, String rulesDetailId, double annualAmount,
                                                  int ageLimit, int ageLimit2, int legalKey, int welfareKey, String legalOrWelfare) {
        BigDecimal amount = new BigDecimal("0");
        try {
            RecordSet recordSet = new RecordSet();
            if (distributionMode == 2 || distributionMode == 7) {
                String sql = "select * from kq_entryToLeave where leaveRulesId=? and lowerLimit<=? and upperLimit>?";
                recordSet.executeQuery(sql, rulesDetailId, ageLimit, ageLimit);
                if (recordSet.next()) {
                    double _amount = Util.getDoubleValue(recordSet.getString("amount"), 0);
                    amount = new BigDecimal("" + _amount);
                }
            } else if (distributionMode == 3) {
                String sql = "select * from kq_workingAgeToLeave where leaveRulesId=? and lowerLimit<=? and upperLimit>?";
                recordSet.executeQuery(sql, rulesDetailId, ageLimit, ageLimit);
                if (recordSet.next()) {
                    double _amount = Util.getDoubleValue(recordSet.getString("amount"), 0);
                    amount = new BigDecimal("" + _amount);
                }
            } else if (distributionMode == 4) {
                amount = new BigDecimal("" + annualAmount);
            } else if (distributionMode == 6) {
                String sql = "";
                if (legalOrWelfare.equals("legal")) {
                    sql = "select * from kq_MixModeToLegalLeave where 1=1 and leaveRulesId=" + rulesDetailId;
                    if (legalKey == 0) {
                        sql += " and limit1<=" + ageLimit;
                    } else if (legalKey == 1) {
                        sql += " and limit2<=" + ageLimit2;
                    } else if (legalKey == 2) {
                        sql += " and limit1<=" + ageLimit + " and limit2<=" + ageLimit2;
                    }
                } else {
                    sql = "select * from kq_MixModeToWelfareLeave where 1=1 and leaveRulesId=" + rulesDetailId;
                    if (welfareKey == 0) {
                        sql += " and limit1<=" + ageLimit;
                    } else if (welfareKey == 1) {
                        sql += " and limit2<=" + ageLimit2;
                    } else if (welfareKey == 2) {
                        sql += " and limit1<=" + ageLimit + " and limit2<=" + ageLimit2;
                    }
                }
                sql += " order by id desc ";
                recordSet.executeQuery(sql);
                if (recordSet.next()) {
                    double _amount = Util.getDoubleValue(recordSet.getString("amount"), 0);
                    amount = new BigDecimal("" + _amount);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return amount;
    }


    private static ThreadLocal<SimpleDateFormat> simpleDateFormatThreadLocal = new ThreadLocal<>();

    private static void init() {
        if (simpleDateFormatThreadLocal.get() == null) {
            simpleDateFormatThreadLocal.set(new SimpleDateFormat("yyyy-MM-dd"));
        }
    }

    /**
     * 获取两个日期之间的时间差距有几年
     *
     * @param fromDate 开始时间
     * @param toDate   结束时间
     * @return
     */
    private static int getAgeLimit(String fromDate, String toDate) {
        init();
        int ageLImit = 0;
        try {
            if (toDate.compareTo(fromDate) < 0) {
                return -1;
            }
            Date fd = simpleDateFormatThreadLocal.get().parse(fromDate);
            Date td = simpleDateFormatThreadLocal.get().parse(toDate);
            Instant fInstant = fd.toInstant();
            Instant tInstant = td.toInstant();
            LocalDate localFromDate = LocalDateTime.ofInstant(fInstant, ZoneId.systemDefault()).toLocalDate();
            LocalDate localToDate = LocalDateTime.ofInstant(tInstant, ZoneId.systemDefault()).toLocalDate();

            //LocalDate localFromDate = LocalDate.parse(fromDate);
            //LocalDate localToDate = LocalDate.parse(toDate);
            Period period = Period.between(localFromDate, localToDate);
            ageLImit = period.getYears();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ageLImit;
    }


    /****************************************************下面是正式系统用的****************************************************/

    /**
     * 获取年假、带薪事假、带薪病假、调休的假期余额信息
     *
     * @param resourceId 指定人员ID
     * @param languageId 当前系统语言
     * @return
     */
    public static List<Object> getBalanceInfo(String resourceId, int languageId) {
        String resultStr = "";
        List<Object> dataList = new ArrayList<Object>();
        try {
            /*获取当前日期，当前时间*/
            Calendar today = Calendar.getInstance();
            String lastDayOfLastYear = Util.add0(today.get(Calendar.YEAR) - 1, 4) + "-12-31";
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

            KQLeaveRulesComInfo kqLeaveRulesComInfo = new KQLeaveRulesComInfo();

            Map<String, Object> itemMap = new HashMap<String, Object>();
            List<Object> itemList = new ArrayList<Object>();
            Map<String, Object> dataMap = new HashMap<String, Object>();

            String sql = "select * from kq_LeaveRules where 1=1 and (isDelete is null or isDelete<>1) and isEnable=1 and leaveCode in ('annualLeave','paidCompassionateLeave','paidSickLeave','vacationLeave')";
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(sql);
            while (recordSet.next()) {
                String ruleId = recordSet.getString("id");
                String leaveCode = recordSet.getString("leaveCode");

                String title = "";
                switch (leaveCode) {
                    case "annualLeave":
                        title = SystemEnv.getHtmlLabelName(501313, languageId);
                        break;
                    case "paidCompassionateLeave":
                        title = SystemEnv.getHtmlLabelName(501314, languageId);
                        break;
                    case "paidSickLeave":
                        title = SystemEnv.getHtmlLabelName(501315, languageId);
                        break;
                    case "vacationLeave":
                        title = SystemEnv.getHtmlLabelName(31297, languageId);
                        break;
                    default:
                        break;
                }

                String minimumUnit = kqLeaveRulesComInfo.getMinimumUnit(ruleId);//最小请假单位：1-按天请假、2-按半天请假、3-按小时请假、4-按整天请假

                String currentYearAmount = getRestAmount(resourceId, ruleId, currentDate, true, false);//本年剩余年假时长
                String allAmount = getRestAmount(resourceId, ruleId, currentDate, true, true);//当前剩余年假时长
                String lastYearAmount = String.format("%.2f", Util.getDoubleValue(allAmount, 0) - Util.getDoubleValue(currentYearAmount, 0));;//历年剩余年假时长

                itemList = new ArrayList<Object>();
                if (!leaveCode.equals("vacationLeave")) {
                    itemMap = new HashMap<String, Object>();
                    itemMap.put("name", KQUnitBiz.isLeaveHour(minimumUnit+"") ? SystemEnv.getHtmlLabelName(513286, languageId) : SystemEnv.getHtmlLabelName(513287, languageId));
                    itemMap.put("value", lastYearAmount);
                    itemList.add(itemMap);
                    itemMap = new HashMap<String, Object>();
                    itemMap.put("name", KQUnitBiz.isLeaveHour(minimumUnit+"") ? SystemEnv.getHtmlLabelName(501311, languageId) : SystemEnv.getHtmlLabelName(132012, languageId));
                    itemMap.put("value", currentYearAmount);
                    itemList.add(itemMap);
                }
                itemMap = new HashMap<String, Object>();
                itemMap.put("name", KQUnitBiz.isLeaveHour(minimumUnit+"") ? SystemEnv.getHtmlLabelName(513288, languageId) : SystemEnv.getHtmlLabelName(513289, languageId));
                itemMap.put("value", allAmount);
                itemList.add(itemMap);

                dataMap = new HashMap<String, Object>();
                dataMap.put("detail", itemList);
                dataMap.put("title", title);
                dataMap.put("leaveCode", leaveCode);

                dataList.add(dataMap);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataList;
    }

    /**
     * 获取正式系统请假流程协同区所需的相关数据
     *
     * @param resourceId
     * @param searchYear
     * @return
     */
    public static Map<String, Object> getSynergyZoneInfo(String resourceId, String searchYear) {
        Map<String, Object> resultMap = new HashMap<String, Object>();
        try {
            /*获取当前日期，当前时间*/
            Calendar today = Calendar.getInstance();
            String lastDayOfLastYear = Util.add0(today.get(Calendar.YEAR) - 1, 4) + "-12-31";
            String currentDate = Util.add0(today.get(Calendar.YEAR), 4) + "-" +
                    Util.add0(today.get(Calendar.MONTH) + 1, 2) + "-" +
                    Util.add0(today.get(Calendar.DAY_OF_MONTH), 2);

            double usedAmount1 = 0.00;//年假已用
            double restAmount1 = 0.00;//年假剩余

            String sql = "select * from kq_leaveRules where 1=1 and (isDelete is null or isDelete <>1) and leaveCode='annualLeave'";
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(sql);
            if (recordSet.next()) {
                String ruleId = recordSet.getString("id");

                sql = "select * from kq_BalanceOfLeave where belongYear=? and resourceId=? and leaveRulesId=?";
                recordSet.executeQuery(sql, searchYear, resourceId, ruleId);
                if (recordSet.next()) {
                    usedAmount1 = Util.getDoubleValue(recordSet.getString("usedAmount"), 0.00);
                }
                restAmount1 = Util.getDoubleValue(getRestAmount(resourceId, ruleId, currentDate, true, false), 0.00);
            }

            double usedAmount2 = 0.00;//带薪病假已用
            double restAmount2 = 0.00;//带薪病假剩余

            sql = "select * from kq_leaveRules where 1=1 and (isDelete is null or isDelete <>1) and leaveCode='paidSickLeave'";
            recordSet.executeQuery(sql);
            if (recordSet.next()) {
                String ruleId = recordSet.getString("id");

                sql = "select * from kq_BalanceOfLeave where belongYear=? and resourceId=? and leaveRulesId=?";
                recordSet.executeQuery(sql, searchYear, resourceId, ruleId);
                if (recordSet.next()) {
                    usedAmount2 = Util.getDoubleValue(recordSet.getString("usedAmount"), 0.00);
                }
                restAmount2 = Util.getDoubleValue(getRestAmount(resourceId, ruleId, currentDate, true, false), 0.00);
            }

            double usedAmount3 = 0.00;//带薪事假已用
            sql = "select * from kq_leaveRules where 1=1 and (isDelete is null or isDelete <>1) and leaveCode='paidCompassionateLeave'";
            recordSet.executeQuery(sql);
            if (recordSet.next()) {
                String ruleId = recordSet.getString("id");

                sql = "select * from kq_BalanceOfLeave where belongYear=? and resourceId=? and leaveRulesId=?";
                recordSet.executeQuery(sql, searchYear, resourceId, ruleId);
                if (recordSet.next()) {
                    usedAmount3 = Util.getDoubleValue(recordSet.getString("usedAmount"), 0.00);
                }
            }


            double restAmount4 = 0.00;//调休剩余
            sql = "select * from kq_leaveRules where 1=1 and (isDelete is null or isDelete <>1) and leaveCode='vacationLeave'";
            recordSet.executeQuery(sql);
            if (recordSet.next()) {
                String ruleId = recordSet.getString("id");

                restAmount4 = Util.getDoubleValue(getRestAmount(resourceId, ruleId, currentDate, true, false), 0.00);
            }

            resultMap.put("usedAmount1", usedAmount1);
            resultMap.put("restAmount1", restAmount1);
            resultMap.put("usedAmount2", usedAmount2);
            resultMap.put("restAmount2", restAmount2);
            resultMap.put("usedAmount3", usedAmount3);
            resultMap.put("restAmount4", restAmount4);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultMap;
    }

    /**
     * 获取年假开始日期
     *
     * @param resourceId 人员ID
     * @return
     */
    private static String getAnnualDate(String resourceId) {
        String result = "";
        try {
            String sql = "select t2.field3 annualDate from hrmResource t1 left join cus_fielddata t2 on t1.id=t2.id and t2.scope='HrmCustomFieldByInfoType' and t2.scopeid=-1 where t1.id=" + resourceId;
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(sql);
            if (recordSet.next()) {
                String annualDate = recordSet.getString("annualDate");
                if (annualDate.contains("\\/")) {
                    try {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/M/dd");
                        Date date = simpleDateFormat.parse(annualDate);
                        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        annualDate = simpleDateFormat.format(date);
                    } catch (Exception e) {
                        e.printStackTrace();
                        annualDate = "";
                    }
                }
                Pattern pattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2}$");
                Matcher matcher_1 = pattern.matcher(annualDate);
                if (!annualDate.equals("") && matcher_1.matches()) {
                    result = annualDate;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void logOvertimeMap(Map<String, Object> overtimeLogMap,Object params, String keys){
        if(overtimeLogMap != null){
            overtimeLogMap.put(keys, params);
        }
    }
    /**
     * 获取育儿假开始日期
     *
     * @param resourceId 人员ID
     * @return
     */
    public static ArrayList<String> getParentalLeaveDate(String resourceId) {
        ArrayList<String> list = new ArrayList<>();
        try {
            String sql = "select birthday from hrmfamilyinfo where WhetherChildren=1 and resourceid=? order by birthday ";
            RecordSet recordSet = new RecordSet();
            recordSet.executeQuery(sql,resourceId);
            while (recordSet.next()) {
                String annualDate = recordSet.getString("birthday");
                if (annualDate.contains("\\/")) {
                    try {
                        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/M/dd");
                        Date date = simpleDateFormat.parse(annualDate);
                        simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
                        annualDate = simpleDateFormat.format(date);
                    } catch (Exception e) {
                        e.printStackTrace();
                        annualDate = "";
                    }
                }
                Pattern pattern = Pattern.compile("^\\d{4}\\-\\d{2}\\-\\d{2}$");
                Matcher matcher_1 = pattern.matcher(annualDate);
                if (!annualDate.equals("") && matcher_1.matches()) {
                    list.add(annualDate);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }


}
