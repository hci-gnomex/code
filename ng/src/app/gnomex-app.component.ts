/*
 * Copyright (c) 2016 Huntsman Cancer Institute at the University of Utah, Confidential and Proprietary
 */
import {Component, ViewChild, OnInit} from "@angular/core";
import {Router} from "@angular/router";
import {UserService} from "@hci/user";
import {AppHeaderComponent} from "@hci/app-header";
import {NavigationAction, NavigationItem, PrimaryNavigationItem, PrimaryNavigationItemGroup} from "@hci/navigation";
import {AppFooterComponent} from "@hci/app-footer";

/**
 * The gnomex application component.
 *
 * @author jason.holmberg <jason.holmberg@hci.utah.edu>
 */
@Component({
  selector: "gnomex-app",
  providers: [],
  /*styles: [require("./gnomex-app.component.less")],*/
  template: require("./gnomex-app.component.html")
})
export class GnomexAppComponent implements OnInit {
  public isCollapsed: boolean = true;
  public status: {isopen: boolean} = {isopen: false};

  private appNameTitle: string = "Gnomex";

  @ViewChild(AppHeaderComponent)
  private _appHdrCmpt: AppHeaderComponent;

  @ViewChild(AppFooterComponent)
  private _appFooterCmpt: AppFooterComponent;

  constructor(private userService: UserService,
              private router: Router) {
  }

  ngOnInit() {
    this.setupHeaderComponent();
    this.setupFooterComponent();
  }

  private setupFooterComponent() {
    this._appFooterCmpt.appName = this.appNameTitle;
    this._appFooterCmpt.copyright = "Huntsman Cancer Institute";
  }

  /**
   * A convenience methods to fetch the necessary items for the Experiments dropdown.
   *
   * TODO - Hook up experiments menu items service.
   * These items might be dynamic so perhaps the call to a service the will retrieve/compute these items would happen here.
   */
  private experimentSropdownItems(): PrimaryNavigationItem[] {
    let items: PrimaryNavigationItem[] =  [
      new PrimaryNavigationItem({name: "Browse Experiments", route: "/experiments", cssClass: "gx-nav-exp-browse", iconClass: "gx-nav-icon gx-nav-exp-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Experiment Order for High Throughput Genomics", route: "/experiments/new/htg", cssClass: "gx-nav-exp-htg", iconClass: "gx-nav-icon gx-nav-exp-new-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Experiment Order for Molecular Diagnostics", route: "/experiments/new/mdiag", cssClass: "gx-nav-exp-mdiag", iconClass: "gx-nav-icon gx-nav-exp-new-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Experiment Order for DNA Sequencing", route: "/experiments/new/dnaseq", cssClass: "gx-nav-exp-dnaseq", iconClass: "gx-nav-icon gx-nav-exp-new-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Experiment Order for Genomics Core", route: "/experiments/new/gcore", cssClass: "gx-nav-exp-gcore", iconClass: "gx-nav-icon gx-nav-exp-new-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Experiment Order for Bioinformatics", route: "/experiments/new/bioinf", cssClass: "gx-nav-exp-bioinf", iconClass: "gx-nav-icon gx-nav-exp-new-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "Add Additional Illumina Sequencing Lanes", route: "/experiments/new/illseqlane", cssClass: "gx-nav-exp-illumina", iconClass: "gx-nav-icon gx-nav-exp-edit-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Project", route: "/experiments/new/project", cssClass: "gx-nav-exp-new-project", iconClass: "gx-nav-exp-new-project-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "Upload Experiment data generated at third party facility", route: "/experiments/upload", cssClass: "gx-nav-exp-upload", iconClass: "gx-nav-icon gx-nav-exp-upload-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "New Billing Account", route: "/experiments/new/billing", cssClass: "gx-nav-exp-new-billing", iconClass: "gx-nav-icon gx-nav-exp-new-billing-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "Orders", route: "/experiments/orders", cssClass: "gx-nav-exp-orders", iconClass: "gx-nav-icon gx-nav-exp-orders-icon"/*, visibility: "EXPERIMENTS"*/}),
      new PrimaryNavigationItem({name: "Bulk Sample Sheet Import", route: "/experiments/new/billing", cssClass: "gx-nav-exp-bulk-sample", iconClass: "gx-nav-icon gx-nav-exp-bulk-sample-icon"/*, visibility: "EXPERIMENTS"*/})
    ];
    return items;
  }

  private isAuthenticated(): boolean {
    let authenticated: boolean = false;
      this.userService.isAuthenticated().subscribe(
      authd => {
        authenticated = authd;
      },
      error => console.error(error)
    );
    return false;
  }

  private setupHeaderComponent() {
    this._appHdrCmpt.iconPath = "./assets/gnomex_logo.png";
    //this._appHdrCmpt.title = this.appNameTitle;
    this._appHdrCmpt.homeRoute = "/";
    this._appHdrCmpt.navbarClasses = "bg-faded";


    // Currently no roles guard these menus, but they can be configure
    // Only show the menus if authenticated, are there menure that should be shown if not authenticated?
    if (this.isAuthenticated()) {
      this._appHdrCmpt.primaryMenuGroups = [
        new PrimaryNavigationItemGroup(
          {
            name: "main",
            items: [
              new PrimaryNavigationItem({
                name: "Experiments", route: "/experiments", iconClass: "gx-nav-icon gx-nav-exp-icon", isDropdown: true,
                items: this.experimentSropdownItems()
              }),
              new PrimaryNavigationItem({
                name: "Analysis",
                route: "/analysis",
                iconClass: "gx-nav-icon gx-nav-analysis-icon"
              }),
              new PrimaryNavigationItem({
                name: "Data Tracks",
                route: "/datatracks",
                iconClass: "gx-nav-icon gx-nav-datatrack-icon"
              }),
              new PrimaryNavigationItem({
                name: "Topics",
                route: "/topics",
                iconClass: "gx-nav-icon gx-nav-topics-icon"
              }),
              new PrimaryNavigationItem({
                name: "Workflow",
                route: "/workflow",
                iconClass: "fa fa-random"/*, visibility: "LIMS"*/
              }),
              new PrimaryNavigationItem({name: "Products", route: "/products", iconClass: "fa fa-shopping-basket"}),
              new PrimaryNavigationItem({name: "Billing", route: "/billing", iconClass: "fa fa-money"}),
              new PrimaryNavigationItem({name: "Reports", route: "/reports", iconClass: "fa fa-file"})
            ]
          }
        )
      ];


      /*
       * TODO: BHY (08/12/16) - This changes based on the users current state (authenticated or not) as informed by the user
       * service.
       */
      this._appHdrCmpt.userMenuDropdownClass = "gx-user-menu fa fa-user-circle";
      this._appHdrCmpt.userMenuItems = [
        new NavigationItem({name: "My Account", route: "foo"}),
      ];
      this._appHdrCmpt.userMenuActions = [new NavigationAction(
        {
          name: "Logout",
          action: () => {
            if (this.userService.logout()) {
              this.router.navigate(["/login"]);
            }
          }
        })];

      /*
       * TODO: JEH (10/28/16) - Update this ADMIN role when we know what it is. May also add roles to individual nav items
       * The role specified below is require to expose the admin menu
       */
      this._appHdrCmpt.adminMenuDropdownClass = "fa fa-gear gx-admin-menu";
      this._appHdrCmpt.adminRole = "ADMIN";
      // Each item/action in the admin menu may be guarded by a different role or none at all.
      this._appHdrCmpt.adminMenuItems = [
        new NavigationItem({name: "Users & Groups", route: "/admin/usersgroups", iconClass: "fa fa-users"}),
        new NavigationItem({name: "Configure", route: "/config", iconClass: "fa fa-gear"}),
      ];
      /* add back when/if necessary
       this._appHdrCmpt.adminActionItems = [
       new NavigationAction(
       {
       name: "Admin Action",
       action: () => {
       alert("Admin action menu item executed!");
       }
       })
       ];*/
      // TODO: BHY (08/12/16) - Enable this when we identify the use case.
      // this._appHdrCmpt.headerAction = new HciHeaderAction("Create Foo", this._demoAppSvc.buttonAction());
      // TODO: BHY (08/12/16) - Enable this when we identify the use case.
      // this._appHdrCmpt.searchFn = this._demoAppSvc.searchFn();
    } else {
      // Make sure we hide the admin and user menus
      this._appHdrCmpt.userMenuEnabled = false;
    }
  }
}
