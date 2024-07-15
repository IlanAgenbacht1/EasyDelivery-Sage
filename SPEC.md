# App Specification Document

## Overview
This document outlines the requirements and flow for managing trips in the app. The app should list active trips on startup, allow drivers to select and start a trip, record deliveries, and handle trip completion.

## Requirements

### Trip Listing on Startup
- On startup, the app should list all active trips.
- Each trip will have a corresponding JSON file named after the trip (e.g., `TripName.json`).

### Trip Selection and Initialization
- The initial screen should display a list of available trips for the driver to select from.
- Upon selecting a trip:
  - The corresponding JSON file is downloaded.
  - The file is moved to the `in_progress/` folder.
  - A trip start record is created.

### Delivery Recording
- For each delivery within a trip:
  - A delivery record (JSON file) is created.
  - Attached artifacts such as signatures and photos are stored in the `completed/TripName/` folder.

### Trip Completion
- Upon completing the trip:
  - A final record is created.
  - A summary JSON file of all deliveries made during the trip is generated.

### Data Sync and Integration
- The app will utilize Dropbox for syncing data, allowing progress tracking by mining the Dropbox folder.
- Once the trip is complete, the data will be read back into the host system for further processing.


## Notes
- Ensure Dropbox syncs data consistently, though it is not essential.
- Further refinement to this specification is acceptable to improve the workflow or accommodate additional requirements.

