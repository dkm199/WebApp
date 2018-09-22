#compile 
1) sbt compile

#This runs web server
2) sbt run

#use curl commands below 

# To Add doctor (returns doctor Id)
 curl -X POST   "http://localhost:8080/doctors?firstName=SomeName&lastName=LastName"
 
# To see doctors
 curl -X GET http://localhost:8080/doctors
 
 # To Add Appointment (Allowed pTypes = "NewPatient" Or "FollowUp") (returns appt id)
 (I would have made this json instead of queries, if time permitted)
 curl -X POST "http://localhost:8080/doctor/appointment?doctorId=0&startTime=1537650000000&duration=5&firstName=dds&lastName=433&pType=NewPatient"
 
 # To Get Appointments for a given day and given doctor
 curl -X GET "http://localhost:8080/doctor/appointment?doctorId=0&day=1537650000000"
 
 # To Delete an appointment
 curl -X DELETE "http://localhost:8080/doctor/appointment?doctorId=0&appointmentId=0"


