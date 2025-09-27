/**
 * HAR File Uploader Module
 * Handles drag-and-drop and file input for uploading HAR files
 */

export class HarUploader {
    /**
     * @param {Object} options - Configuration options
     * @param {string} options.dropAreaId - ID of the drop area element
     * @param {string} options.fileInputId - ID of the file input element
     * @param {string} options.selectButtonId - ID of the select button
     * @param {string} options.messagesId - ID of the messages container
     */
    constructor({dropAreaId, fileInputId, selectButtonId, messagesId}) {
        this.dropArea = document.getElementById(dropAreaId);
        this.fileInput = document.getElementById(fileInputId);
        this.selectButton = document.getElementById(selectButtonId);
        this.messages = document.getElementById(messagesId);

        if (!this.dropArea || !this.fileInput || !this.selectButton || !this.messages) {
            throw new Error('Required elements not found in the DOM');
        }

        // Drag and drop events
        ['dragenter', 'dragover'].forEach(eventName => {
            this.dropArea.addEventListener(eventName, this.handleDragOver.bind(this));
        });

        ['dragleave', 'dragend'].forEach(eventName => {
            this.dropArea.addEventListener(eventName, this.handleDragLeave.bind(this));
        });

        this.dropArea.addEventListener('drop', this.handleDrop.bind(this));
        this.fileInput.addEventListener('change', this.handleFileSelect.bind(this));
        this.selectButton.addEventListener('click', () => this.fileInput.click());
    }
    /**
     * Handle drag over event
     * @param {DragEvent} event
     */
    handleDragOver(event) {
        event.preventDefault();
        if (event.dataTransfer) {
            event.dataTransfer.dropEffect = 'copy';
        }
        this.dropArea.classList.add('dragover');
    }

    /**
     * Handle drag leave event
     */
    handleDragLeave() {
        this.dropArea.classList.remove('dragover');
    }

    /**
     * Handle drop event
     * @param {DragEvent} event
     */
    handleDrop(event) {
        event.preventDefault();
        this.dropArea.classList.remove('dragover');

        const files = event.dataTransfer?.files;
        if (files?.length) {
            this.handleFiles(files);
        }
    }

    /**
     * Handle file input change event
     * @param {Event} event
     */
    handleFileSelect(event) {
        const {files} = event.target;
        if (files?.length) {
            this.handleFiles(files);
        }
        // Reset the input to allow selecting the same file again
        this.fileInput.value = '';
    }

    /**
     * Process multiple files
     * @param {FileList} files
     */
    handleFiles(files) {
        Array.from(files).forEach(file => this.uploadFile(file));
    }

    /**
     * Generate a name for the HAR file
     * @param {File} file
     * @returns {string}
     */
    deriveHarName(file) {
        return file.name.replace(/\.(har|json)$/i, '') || `unknown ${new Date().toISOString()}`;
    }

    /**
     * Show a message in the UI
     * @param {string} text - Message text
     * @param {'info'|'error'|'success'} [type='info'] - Message type
     */
    showMessage(text, type = 'info') {
        const entry = document.createElement('li');
        entry.textContent = text;

        if (type === 'error') {
            entry.classList.add('upload-messages__error');
        } else if (type === 'success') {
            entry.classList.add('upload-messages__success');
        }

        this.messages.prepend(entry);
    }

    /**
     * Upload a file to the server
     * @param {File} file
     */
    async uploadFile(file) {
        if (!file) return;

        const isHar = file.name.toLowerCase().endsWith('.har');
        const looksJson = file.type.includes('json');

        if (!isHar && !looksJson) {
            this.showMessage(`Skipping ${file.name}: only HAR files are supported.`, 'error');
            return;
        }

        try {
            const content = await this.readFileAsText(file);
            if (!content) {
                this.showMessage(`Failed to read ${file.name}: empty file.`, 'error');
                return;
            }

            // Validate JSON
            JSON.parse(content);
            const harName = this.deriveHarName(file);

            await this.sendToServer(harName, content);
            this.showMessage(`Uploaded ${file.name} as ${harName}.`, 'success');

        } catch (error) {
            const message = error instanceof Error ? error.message : String(error);
            this.showMessage(`Failed to process ${file.name}: ${message}`, 'error');
        }
    }

    /**
     * Read file as text
     * @param {File} file
     * @returns {Promise<string>}
     */
    readFileAsText(file) {
        return new Promise((resolve, reject) => {
            const reader = new FileReader();

            reader.onload = () => {
                resolve(reader.result?.toString() || '');
            };

            reader.onerror = () => {
                reject(reader.error || new Error('Failed to read file'));
            };

            reader.readAsText(file);
        });
    }

    /**
     * Send data to the server
     * @param {string} harName
     * @param {string} content
     */
    async sendToServer(harName, content) {
        const response = await fetch(`/api/har/${encodeURIComponent(harName)}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: content,
        });

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(response.statusText || errorText);
        }

        return response;
    }

    /**
     * Clean up event listeners
     */
    destroy() {
        // Remove all event listeners
        ['dragenter', 'dragover', 'dragleave', 'dragend', 'drop'].forEach(eventName => {
            this.dropArea.removeEventListener(eventName, this.handleDragOver);
            this.dropArea.removeEventListener(eventName, this.handleDragLeave);
        });

        this.fileInput.removeEventListener('change', this.handleFileSelect);
        this.selectButton.removeEventListener('click', this.handleFileSelect);
    }
}
