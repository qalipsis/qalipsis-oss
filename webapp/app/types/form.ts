export interface FormMenuOption {
    /**
     * The label text of the option
     */
    label: string;
    /**
     * The value of the option
     */
    value: string;
    /**
     * A flag to indicate if the option is disabled
     */
    disabled?: boolean;
    /**
     * Image URL to display next to the label, when the option has a visual identity, such as a zone flag
     */
    imagePath?: string;
    /**
     * A more detailed definition of the option, displayed under its label
     */
    description?: string;
}

export type FormInputType =
    | "number"
    | "button"
    | "time"
    | "reset"
    | "submit"
    | "image"
    | "text"
    | "search"
    | "checkbox"
    | "radio"
    | "hidden"
    | "color"
    | "range"
    | "date"
    | "url"
    | "email"
    | "week"
    | "month"
    | "tel"
    | "datetime-local"
    | "file"
    | "password";
