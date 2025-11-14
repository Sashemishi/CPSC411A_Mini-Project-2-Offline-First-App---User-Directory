# CPSC411A_Mini-Project-2-Offline-First-App---User-Directory

App Overview: The purpose of this app is to fetch data from an API, display it, and save it for when the device is offline so it can still display it.

<img width="621" height="1353" alt="Screenshot 2025-11-14 092619" src="https://github.com/user-attachments/assets/f67dcaf7-b380-4149-aa8e-af7a69bf2d62" />

The way the code works is using retrofit to fetch data from the provided url and saves it, then reading from the local Room database while checking if the data in the api changes. If it does change, it'll update the data in the database.
