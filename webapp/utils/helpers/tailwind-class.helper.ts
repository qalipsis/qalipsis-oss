export class TailwindClassHelper {
    static circleCheckBoxBaseClass = 'w-7 h-7 rounded-full border border-solid border-primary-400 text-center flex items-center justify-center cursor-pointer';
    static radioButtonClass = 'relative appearance-none w-4 h-4 border border-gray-300 rounded-full bg-white disabled:bg-gray-50 dark:disabled:bg-gray-600 disabled:cursor-not-allowed enabled:hover:border-primary-500 cursor-pointer shrink-0 checked:border-primary-500'
    static radioButtonActiveClass = "after:content-[''] after:w-[10px] after:h-[10px] after:mx-auto after:my-[2px] after:rounded-full after:hidden checked:after:block checked:after:bg-primary-500";
    static checkBoxClass = 'relative appearance-none w-4 h-4 border border-gray-300 rounded-sm bg-white dark:bg-gray-100 disabled:bg-gray-50 dark:disabled:bg-gray-600 disabled:cursor-not-allowed enabled:hover:border-primary-500 cursor-pointer shrink-0 enabled:checked:bg-primary-500 enabled:checked:border-0 outline-none';
    static checkBoxMarkerClass = "enabled:after:content-[''] enabled:after:hidden enabled:after:w-[6px] enabled:after:h-3 enabled:after:border-r-2 enabled:after:border-b-2 enabled:after:border-solid enabled:after:border-white enabled:after:rotate-45 enabled:after:ml-[5px] enabled:checked:after:block";
    static checkBoxIndeterminateClass = "enabled:before:content-[''] enabled:before:inline-block enabled:before:rounded-sm enabled:before:ml-[2px] enabled:before:mb-[3px] enabled:before:w-[10px] enabled:before:h-[10px] enabled:before:bg-primary-500";
    static formInputWrapperClass = 'border px-2 py-1 h-10 border-solid w-full rounded-md flex items-center justify-between has-[:disabled]:bg-gray-50 has-[:disabled]:dark:bg-gray-700 has-[:disabled]:text-gray-400';
    static formInputWrapperActiveClass = 'border-gray-200 has-[:focus]:border-primary-500 has-[:enabled]:hover:border-primary-500 has-[:enabled]:hover:bg-primary-50 dark:has-[:enabled]:hover:bg-gray-800';
    static formInputWrapperErrorClass = 'border-red-600';
    static formInputClass = 'outline-none w-full bg-transparent enabled:autofill:bg-transparent disabled:cursor-not-allowed';
    static formDropdownClass = 'relative inline-block text-left';
    static formDropdownPanelClass = 'absolute right-0 rounded-md bg-white dark:bg-gray-950 shadow-xl focus:outline-none p-2 z-20 max-h-60 overflow-y-auto';
    static formDropdownTransitionEnterActiveClass = 'transition duration-100 ease-out';
    static formDropdownTransitionEnterFromClass = 'transform scale-95 opacity-0';
    static formDropdownTransitionEnterToClass = 'transform scale-100 opacity-100';
    static formDropdownTransitionLeaveActiveClass = 'transition duration-75 ease-in';
    static formDropdownTransitionLeaveFromClass = 'transform scale-100 opacity-100';
    static formDropdownTransitionLeaveToClass = "transform scale-95 opacity-0";
    static formDropdownOptionClass = 'w-full relative py-2 px-3 rounded-md mb-1';
    static formDropdownOptionHoverClass = 'hover:bg-primary-50 dark:hover:bg-gray-700';
    static formDropdownOptionDisabledClass = 'text-gray-300 dark:text-gray-500 cursor-not-allowed';
    static formDropdownOptionActiveClass = "bg-gray-100 dark:bg-gray-800 after:content-[''] after:absolute after:w-2 after:h-2 after:rounded-full after:right-2 after:top-auto after:bg-primary-500";
    static menuPanelBaseClass = 'absolute right-0 z-10 py-2 bg-white dark:bg-gray-900 w-fit shadow-xl rounded-md';
    static menuWrapperBaseClass = 'flex items-center cursor-pointer hover:bg-primary-50 dark:hover:bg-gray-800';
    static menuItemBaseClass = 'flex items-center h-full w-32 px-4 py-3 hover:text-primary-500 text-gray-700 dark:text-gray-100';
}