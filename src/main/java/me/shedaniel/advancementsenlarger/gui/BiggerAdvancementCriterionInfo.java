/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package me.shedaniel.advancementsenlarger.gui;

/**
 *
 * @author gbl
 */
public class BiggerAdvancementCriterionInfo {
    private String name;
    private boolean obtained;
    
    BiggerAdvancementCriterionInfo(String name, boolean obtained) {
        this.name = name;
        this.obtained = obtained;
    }
    
    public boolean getObtained() {
        return obtained;
    }
    
    public String getName() {
        return name;
    }
}
