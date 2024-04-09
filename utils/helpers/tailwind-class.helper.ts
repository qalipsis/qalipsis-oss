export class TailwindClassHelper {
    static primaryColorFilterClass = '[filter:brightness(0%)_saturate(100%)_invert(61%)_sepia(38%)_saturate(657%)_hue-rotate(132deg)_brightness(89%)_contrast(91%)]';
    static grayColorFilterClass = '[filter:brightness(0%)_saturate(100%)_invert(67%)_sepia(18%)_saturate(105%)_hue-rotate(145deg)_brightness(90%)_contrast(83%)]';
    static primaryColorFilterHoverClass = 'hover:[filter:brightness(0%)_saturate(100%)_invert(61%)_sepia(38%)_saturate(657%)_hue-rotate(132deg)_brightness(89%)_contrast(91%)]';
    static searchInputBaseClass = 'flex items-center border border-solid h-10 rounded-md w-fit outline-none';
    static circleCheckBoxBaseClass = 'w-7 h-7 rounded-full border border-solid border-primary-400 text-center flex items-center justify-center cursor-pointer';
    static baseButtonClass = 'h-10 px-3 py-2 text-base rounded-md min-w-32 flex items-center justify-center disabled:bg-gray-100 disabled:text-gray-500 disabled:cursor-not-allowed';
    static filledButtonClass = 'enabled:bg-primary-400 enabled:text-white enabled:hover:bg-primary-500';
    static outlineButtonClass = 'border border-solid border-gray-300 enabled:text-gray-800 enabled:hover:[filter:brightness(0%)_saturate(100%)_invert(61%)_sepia(38%)_saturate(657%)_hue-rotate(132deg)_brightness(89%)_contrast(91%)]';
    static seriesOptionItemClass = 'border border-solid align-baseline border-gray-200 rounded-md h-10 py-1 px-2 w-full cursor-pointer hover:bg-gray-50'
    static scenarioDropdownItemClass = 'h-8 min-w-56 relative py-2 px-3 rounded-md mb-1';
    static scenarioDropdownItemActiveClass = "bg-gray-100 after:content-[''] after:absolute after:w-2 after:h-2 after:rounded-full after:right-2 after:top-auto after:bg-primary-500";
}