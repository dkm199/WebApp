
import DoctorAppointmentInfo.{Appointment, Appointments, Doctor, Doctors, PatientInfo, jsonFormat1, jsonFormat2}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, StatusCodes, _}
import akka.http.scaladsl.server.Directives.{complete, _}
import spray.json.{DefaultJsonProtocol}
import scala.collection.mutable
import scala.io.StdIn
import scala.util.{Failure, Success, Try}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.stream.ActorMaterializer
import spray.json.DefaultJsonProtocol._

object WebServer {


  def main(args: Array[String]) {
    implicit val system = ActorSystem("doctor-calender-backend")
    implicit val materializer = ActorMaterializer()
    // needed for the future flatMap/onComplete in the end
    implicit val executionContext = system.dispatcher
    implicit val doctorFormat = jsonFormat3(Doctor)
    implicit val patientFormat = jsonFormat3(PatientInfo)
    implicit val appointmentFormat = jsonFormat4(Appointment)
    implicit val doctorsFormat = jsonFormat1(Doctors)
    implicit val appointmentsFormat = jsonFormat1(Appointments)


    val route =
      path("doctors") {
        post {
          parameter("firstName", "lastName") { (firstName, lastName) =>
            DoctorAppointmentInfo.addDoctor(firstName, lastName) match {
              case Success(id) =>
                complete(id)
              case Failure(e) =>
                complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, e.getMessage))
            }
          }
        } ~
        get {
          DoctorAppointmentInfo.getAllDoctors() match {
            case Success(doctors) =>
              complete(doctors)
            case Failure(e) =>
              complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, e.getMessage))
          }
        }

      } ~ pathPrefix("doctor") {
        path("appointment") {
          get {
            parameter("doctorId", "day".as[Long]) { (doctorId, day) =>
              DoctorAppointmentInfo.getAppointments(doctorId, day) match {
                case Success(appointments) =>
                  complete(appointments)
                case Failure(e) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, e.getMessage))
              }
            }
          } ~
          delete {
            parameter("doctorId", "appointmentId") { (doctorId, appointementId) =>
              DoctorAppointmentInfo.delete(doctorId, appointementId) match {
                case Success(_) =>
                  complete(StatusCodes.OK)
                case Failure(e) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, e.getMessage))
              }
            }
          } ~
          post {
            // I'd like to take this as JSON. But going with parameters for now
            parameter("doctorId","startTime".as[Long], "duration".as[Long], "firstName", "lastName", "pType") {
              (doctorId, startTime, duration, firstName, lastName, pType) =>
              DoctorAppointmentInfo.addAppointment(doctorId, startTime, duration, firstName, lastName, pType) match {
                case Success(id) =>
                  complete(id)
                case Failure(e) =>
                  complete(HttpEntity(ContentTypes.`text/html(UTF-8)`, e.getMessage))
              }
            }
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", 8080)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

object DoctorAppointmentInfo extends DefaultJsonProtocol {
  // I would put these classes in separate package.
  // But wasted most of the time setting up akka http server
  // so not going fancy organization for now
  object PatientTypeEnum  extends Enumeration {
    type PatientTypeEnum = Value

    val NewPatient = Value("NewPatient")
    val FollowUp = Value("FollowUp")

    def fromString(input: String): PatientTypeEnum = {
      PatientTypeEnum.values.find(_.toString == input) match {
        case Some(value) => value
        case _ => throw new Exception(s"Invalid Patient Type: $input. Allowed are " +
          s"New Patient or Follow up")
      }
    }
  }

  val allowedAppointmentStartInterval = 15*60*1000 // 15 mins

  case class Doctor(id: String, firstName: String, lastName: String)
  case class PatientInfo(firstName: String, lastName: String,
                         patientType: String)
  // startTime is epoch.. so simpler to handle various TZ issues
  case class Appointment(id: String, patientInfo: PatientInfo, startTime: Long, duration: Long)
  case class Doctors(doctors: Seq[Doctor])
  case class Appointments(appointments: Seq[Appointment])

    // In memory doctors appointment status. if I had time i would have used some
  // in memory database or file etc
  private val doctorAppointmentInfo = mutable.Map.empty[Doctor, Seq[Appointment]]
  private val doctorsList = mutable.Set.empty[Doctor]

  var doctorId:Int = 0
  def addDoctor(firstName: String, lastName: String): Try[String] =  synchronized {
    Try {
      if (firstName.isEmpty || lastName.isEmpty) {
        throw new Exception(s"First name and last name cannot be empty")
      }
      // This allows multiple doctors to have same firstName and lastName
      //  but they will have different ID
      val doctor = Doctor(doctorId.toString, firstName, lastName)
      doctorId += 1
      doctorsList += doctor
      doctorAppointmentInfo += (doctor -> Seq.empty)
      doctor.id
    }
  }

  var appointmentId: Int = 0
  def addAppointment(doctorId: String, startTime: Long, duration: Long, patientFirstName: String,
                     patientLastName: String, patientType: String): Try[String] = synchronized {
    Try {

      doctorsList.find(_.id == doctorId) match {
        case Some(doctor) =>
          val existingAppointments = doctorAppointmentInfo.getOrElse(doctor, Seq.empty)
          val sameTimeAppointments = existingAppointments.filter(_.startTime == startTime)
          if (sameTimeAppointments.size == 3) {
            throw new Exception(s"Doctor(Id: $doctorId) has 3 appointments at the same time already")
          }
          // check that time is at only 15 minute intervals
          if (startTime % allowedAppointmentStartInterval != 0) {
            throw new Exception(s"Appointment can only start at 15 min intervals(ex: 9:15 etc)")
          }

          if (patientFirstName.isEmpty || patientLastName.isEmpty) {
            throw new Exception(s"Patient First name and last name cannot be empty")
          }

          val patientTypeParsed = PatientTypeEnum.fromString(patientType)
          val newAppointment = Appointment(appointmentId.toString, PatientInfo(patientFirstName, patientLastName, patientType),
            startTime, duration)

          doctorAppointmentInfo.update(doctor, existingAppointments :+ newAppointment)
          appointmentId += 1
          newAppointment.id
        case None => throw new Exception(s"Unable to find doctor with ID $doctorId")
      }

    }
  }

  def getAllDoctors(): Try[Seq[Doctor]] = Try {
    doctorsList.toSeq
  }

  def getAppointments(doctorId: String, day: Long): Try[Seq[Appointment]] = Try {
    doctorsList.find(_.id == doctorId) match {
      case Some(doctor) =>
        val existingAppointments = doctorAppointmentInfo.getOrElse(doctor, Seq.empty)
        existingAppointments.filter(appt =>
        appt.startTime >= day && appt.startTime < day + (24*60*60*1000))
      case _ => throw new Exception(s"Unable to find doctor with ID $doctorId")
    }
  }


  def delete(doctorId: String, appointmentId: String): Try[Unit] = Try {
    doctorsList.find(_.id == doctorId) match {
      case Some(doctor) =>
        val existingAppointments = doctorAppointmentInfo.getOrElse(doctor, Seq.empty)
        existingAppointments.find(_.id == appointmentId) match {
          case Some(appointment) =>
            doctorAppointmentInfo.update(doctor, existingAppointments.filterNot(_.id == appointmentId))
          case None => throw new Exception(s"Invalid Appointment id $appointmentId")
        }
      case _ => throw new Exception(s"Unable to find doctor with ID $doctorId")
    }
  }
}