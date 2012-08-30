package views.renderers
{
	import hci.flex.controls.ComboBox;
	import hci.flex.renderers.RendererFactory;
	
	import mx.controls.Alert;
	import mx.core.IFactory;
	import mx.events.ListEvent;
	

	public class ComboBox extends hci.flex.controls.ComboBox
	{ 		public var canChangeByAdminOnly:Boolean = false;
			public var securityDataField:String;

			public static function create(dataProvider:Object, 
										  labelField:String,
										  valueField:String,
										  dataField:String, 
								          securityDataField:String,
								          updateData:Boolean,
									      isRequired:Boolean=false, 
								          canChangeByAdminOnly:Boolean=false,
										  appendBlankRow:Boolean=false):IFactory {
				return RendererFactory.create(views.renderers.ComboBox, {dataProvider: dataProvider, 
																		 labelField: labelField,
																		 valueField: valueField,
																		 dataField: dataField,  
																		 updateData: true,
					 													 securityDataField: securityDataField,
																		 canChangeByAdminOnly: canChangeByAdminOnly,
																		 isRequired: isRequired,
																		 appendBlankRow:appendBlankRow});			
			}
			
			protected function selectTheItem():void {
				for (var i : int = 0; i < dataProvider.length; i++) {
	            	var item:Object = dataProvider[i];
	            	if(item[valueField] == data[dataField]) {
	                      this.selectedIndex = i;
	                      
	                      break;
	                 }
				}
							
			}
			
            protected override function change(event:ListEvent):void {
            	if (securityDataField == null) {
            		if (canChangeByAdminOnly) {
            			if (parentApplication.hasPermission("canWriteAnyObject")) {
            		 		assignData();
            		 		data.@isDirty="Y";
            		 	} else {
            		 		selectTheItem();
			     			Alert.show("This field cannot be changed.");
            		 	}
		     		} else {
	            		assignData();
            		 	data.@isDirty="Y";         		
            		}
            	} else {
			     	if (data[securityDataField] == "Y" || parentApplication.hasPermission("canWriteAnyObject")) {
            		 	assignData();
            		 	data.@isDirty="Y";
		     		} else {		
            		 	selectTheItem();
		     			Alert.show("This field cannot be changed.");
		     		}
            	}
            }	 
			
			override protected function updateDisplayList(w:Number, h:Number):void
			{
				super.updateDisplayList(w, h);
				
				if (data == null || !(data is XML)) {
					this.visible = false;
					return;
				}
			}
			
	}

}