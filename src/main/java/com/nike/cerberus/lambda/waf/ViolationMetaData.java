package com.nike.cerberus.lambda.waf;

import java.util.Date;

public class ViolationMetaData {
    private Date date;
    private int maxRate;

    public ViolationMetaData() {
    }

    public ViolationMetaData(Date date, int maxRate) {
        this.date = date;
        this.maxRate = maxRate;
    }

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public int getMaxRate() {
        return maxRate;
    }

    public void setMaxRate(int maxRate) {
        this.maxRate = maxRate;
    }
}
