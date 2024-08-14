package com.clone.EasyDelivery.Model;

public class ItemParcel {

    private boolean isTextEmpty;

    private boolean completed;

    private String number,hint;

    private String docu, pic, sign, unit;

    private String parcels, time;

    private String driver, company, vehicle;

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getVehicle() {
        return vehicle;
    }

    public void setVehicle(String vehicle) {
        this.vehicle = vehicle;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getParcels() {
        return parcels;
    }

    public void setParcels(String parcels) {
        this.parcels = parcels;
    }

    public boolean isTextEmpty() {
        return isTextEmpty;
    }

    public void setTextEmpty(boolean textEmpty) {
        isTextEmpty = textEmpty;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getDocu() {
        return docu;
    }

    public void setDocu(String docu) {
        this.docu = docu;
    }

    public String getPic() {
        return pic;
    }

    public void setPic(String pic) {
        this.pic = pic;
    }

    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    public ItemParcel(String hint) {
        this.hint = hint;
    }

    public String getHint() {
        return hint;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    public ItemParcel() {

    }

    public String getNumber() {

        return number;
    }

    public void setNumber(String number) {

        this.number = number;
    }

}
