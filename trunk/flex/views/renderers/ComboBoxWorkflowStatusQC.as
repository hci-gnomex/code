package views.renderers
{
	import mx.events.ListEvent;
	
	public class ComboBoxWorkflowStatusQC extends ComboBoxWorkflowStatus
	{
		protected override function initializeFields():void {
			super.initializeFields();
	    	cellAttributeName            = "@qualStatus";
	    }
	    

	}
}