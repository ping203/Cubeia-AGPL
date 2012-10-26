"use strict";
var Poker = Poker || {};

/**
 * Settings that are stored in the local storage,
 * available settings can be found in Poker.Settings.Param
 * @type {Poker.Settings}
 */
Poker.Settings = {
    Param : {
        SWIPE_ENABLED : "settings.swipe",
        FREEZE_COMMUNICATION : "swttings.freeze"
    },
    /**
     * Check whether a boolean property is set to true
     * @param param
     * @param def
     * @return {*}
     */
    isEnabled : function(param,def) {
        if(def==null) {
            def = false;
        }
        return Poker.Utils.loadBoolean(param,def);
    },
    /**
     * stores a property in the local storage
     * @param prop
     * @param value
     */
    setProperty : function(prop,value) {
        Poker.Utils.store(prop,value);
    },
    /**
     * Binds a check box to toggle a boolean property
     * @param checkbox
     * @param param
     */
    bindSettingToggle : function(checkbox,param) {
        var self = this;
        var enabled = this.isEnabled(param,null);
        checkbox.attr("checked",enabled);
        checkbox.change(function(){
            if(checkbox.is(":checked")) {
                self.setProperty(param,true);
            } else {
                self.setProperty(param,false);
            }
        });
    }

};
