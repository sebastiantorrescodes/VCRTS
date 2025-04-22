-- Drop tables in reverse order of creation
DROP TABLE IF EXISTS job_schedule;
DROP TABLE IF EXISTS job_states;
DROP TABLE IF EXISTS pending_requests;
DROP TABLE IF EXISTS allocations;
DROP TABLE IF EXISTS jobs;
DROP TABLE IF EXISTS vehicles;
DROP TABLE IF EXISTS users;

-- Create users table
CREATE TABLE users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    roles VARCHAR(200) NOT NULL,
    password VARCHAR(255) NOT NULL 
);

-- Create vehicles table
CREATE TABLE vehicles (
    vin VARCHAR(20) PRIMARY KEY,
    owner_id INT NOT NULL,
    model VARCHAR(50) NOT NULL,
    make VARCHAR(50) NOT NULL,
    year VARCHAR(4) NOT NULL,
    residency_time VARCHAR(10) NOT NULL,
    registered_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);

-- Create jobs table
CREATE TABLE jobs (
    job_id VARCHAR(50) PRIMARY KEY,
    job_name VARCHAR(100) NOT NULL,
    job_owner_id INT NOT NULL,
    duration VARCHAR(10) NOT NULL,
    deadline VARCHAR(10) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_owner_id) REFERENCES users(user_id) ON DELETE CASCADE
);
/*there should be an allocation table to but i removed it as its not a necessary
function within the program and pending requests will be have changes too

-- Create pending_requests table 
CREATE TABLE pending_requests (
    request_id INT PRIMARY KEY AUTO_INCREMENT,
    request_type ENUM('JOB', 'VEHICLE') NOT NULL,
    data_json TEXT NOT NULL,
    submitted_by_info VARCHAR(255) NOT NULL,
    submission_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
); */

-- Create job_schedule table
CREATE TABLE job_schedule (
    job_id VARCHAR(50) PRIMARY KEY,
    completion_time VARCHAR(20),
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE
);

-- Create job_states table
CREATE TABLE job_states (
    job_id VARCHAR(50) PRIMARY KEY,
    state VARCHAR(20) NOT NULL,
    FOREIGN KEY (job_id) REFERENCES jobs(job_id) ON DELETE CASCADE
);