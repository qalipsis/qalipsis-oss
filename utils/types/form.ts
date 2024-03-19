export interface FormMenuOption {
  /**
   * The label text of the option
   */
  label: string;
  /**
   * The value of the option
   */
  value: string;
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
