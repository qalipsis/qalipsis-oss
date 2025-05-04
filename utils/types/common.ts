export interface TagStyleClass {
    /**
     * The css class for the tag background
     */
    backgroundCssClass: string;
    /**
     * The css class for the text
     */
    textCssClass: string;
}

export interface Tag extends TagStyleClass {
    /**
     * The text of the tag 
     */
    text: string;
}
