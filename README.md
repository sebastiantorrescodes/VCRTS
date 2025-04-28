# VCRTS

## Project Overview

VCRTS is a comprehensive vehicle repair tracking system designed to manage and streamline the process of vehicle repairs and maintenance. The system provides a robust platform for tracking vehicle information, repair jobs, and customer management.

## Key Features

- **User Management**: Secure user authentication and role-based access control
- **Vehicle Management**: Track vehicle details including Owner ID, VIN, make, model, year, and residency time
- **Job Tracking**: Monitor repair jobs with details such as job name, duration, deadline, and status
- **Database Integration**: MySQL database backend for reliable data storage and management
- **GUI Interface**: User-friendly graphical interface for easy system interaction

## Technical Stack

- **Backend**: Java
- **Database**: MySQL
- **Database Connector**: MySQL Connector/J 9.3.0
- **Architecture**: DAO (Data Access Object) pattern for database operations

## System Requirements

- Java Runtime Environment (JRE)
- MySQL Database Server
- MySQL Workbench
- MySQL Connector/J

## Project Structure

- `src/main/`: Contains the main source code
  - `db/`: Database connection and configuration
  - `dao/`: Data Access Objects for database operations
  - `models/`: Data models and entities
  - `gui/`: Graphical user interface components
- `data/`: Data storage and configuration files
- `VCRTS-Tables.sql`: Database schema definition

## Getting Started

1. Set up the MySQL database using the provided `VCRTS-Tables.sql` script.
2. Configure the database connection in `db.env`.
3. Run the application using the `ClientFrame.java` and `ServerFrame.java`.

## Developers

- Sebastian Torres
- Tahsin Ahsan
- Brian Weigand
- Ethan Yambao
