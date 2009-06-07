package views.renderers
{
	import hci.flex.renderers.RendererFactory;
	import mx.controls.TextInput;
	import mx.core.IFactory;

	public class FlowCellSampleConcentrationTextInput extends hci.flex.renderers.TextInput
	{
		public static function create(dataField:String):IFactory {
				return RendererFactory.create(FlowCellSampleConcentrationTextInput, 
				{ _dataField: dataField});			
				  
		}	
		
		
        
    	override protected function updateDisplayList(unscaledWidth:Number, unscaledHeight:Number):void
     	{
          super.updateDisplayList(unscaledWidth,unscaledHeight);
          if (data == null) {
          	return;
          }
          this.setStyle("backgroundColor", data.name() == "WorkItem" && data[_dataField] == '' ? missingRequiredFieldBackground : "0xffffff");
          
          this.editable = true;

	    }
	}
}