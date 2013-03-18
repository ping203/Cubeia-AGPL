"use strict";
var Poker = Poker || {};
Poker.MainMenuManager = Class.extend({
    templateManager : null,
    menuItemTemplate : null,
    init : function(viewManager) {
        this.templateManager = new Poker.TemplateManager();
        this.menuItemTemplate = "menuItemTemplate";
        var self = this;
        $(".main-menu-button").touchSafeClick(function(e){
            self.toggle();
        });
        $(".menu-overlay").touchSafeClick(function(e){
            self.toggle();
        })
        var cashier = new Poker.MenuItem("Cashier","Manage your funds","cashier")
        this.addMenuItem(cashier,null);
        var helpMenuItem = new Poker.MenuItem("Help & rules","Learn how to play poker","help");
        this.addExternalMenuItem(helpMenuItem,function(){
            console.log(Poker.OperatorConfig.getClientHelpUrl());
            window.open(Poker.OperatorConfig.getClientHelpUrl());
        });
        this.addMenuItem(new Poker.MenuItem("Gameplay settings","Muck loosing cards, Muck winning cards","gameplay"),null);
        var soundSettings = new Poker.MenuItem("Sound settings","Turn sound on/off","sound");

        this.addMenuItem(soundSettings,new Poker.SoundSettingsView("#soundSettingsView","sound"));
        var devSettings = new Poker.MenuItem("Development settings","Settings only shown in development","development");
        this.addMenuItem(devSettings,new Poker.DevSettingsView("#devSettingsView",""));

    },
    activeView : null,
    addExternalMenuItem : function(item,activateFunc){
        var self = this;
        item.setActivateFunction(activateFunc);
        item.appendTo(this.templateManager, "#mainMenuList",this.menuItemTemplate);
        if(self.activeView!=null) {
            self.activeView.deactivate();
        }
    },
    addMenuItem : function(item,view) {
        var self = this;
        item.setActivateFunction(function(){
           $("#mainMenuList").find("li").removeClass("active");
            if(self.activeView!=null) {
                self.activeView.deactivate();
            }
            if(view!=null) {
                self.activeView = view;
                view.activate();
            }

        });

        item.appendTo(this.templateManager, "#mainMenuList",this.menuItemTemplate);

    },
    toggle : function() {
        $('.main-menu-container').toggleClass('visible');
        $(".view-container").toggleClass("slided");
        $(".menu-overlay").toggle();
        $("#mainMenuList").find("li").removeClass("active");
        if(this.activeView!=null){
            this.activeView.deactivate();
        }
    }
});

Poker.MenuItem = Class.extend({
    title : null,
    description : null,
    cssClass : null,
    activateFunction : null,

    init : function(title,description,cssClass){

        this.title = title;
        this.description = description;
        this.cssClass = cssClass;
    },
    setActivateFunction : function(func) {
        this.activateFunction = func;
    },
    appendTo : function(templateManager,containerId,template) {
        var self = this;
        var html = templateManager.render(template,
            {
                title:this.title,
                description:this.description,
                cssClass : this.cssClass
            });
        $(containerId).append(html);
        $(containerId).find("."+this.cssClass).touchSafeClick(function(){
            self.activateFunction();
            $(this).addClass("active");
        });
    }
});