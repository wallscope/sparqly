#!/usr/bin/env kotlin

@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.apache.jena:jena-core:3.17.0")
@file:DependsOn("org.apache.jena:jena-arq:3.17.0")
@file:DependsOn("io.github.microutils:kotlin-logging:1.12.0")
@file:DependsOn("com.xenomachina:kotlin-argparser:2.0.7")
@file:DependsOn("org.jline:jline-builtins:3.18.0")
@file:DependsOn("org.jline:jline-reader:3.18.0")
@file:DependsOn("org.jline:jline-terminal:3.18.0")

import com.xenomachina.argparser.ArgParser
import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.datatypes.xsd.XSDDatatype
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.ResultSetFormatter
import org.apache.jena.rdf.model.*
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReaderBuilder
import org.jline.reader.UserInterruptException
import org.jline.terminal.TerminalBuilder
import java.io.File
import java.lang.StringBuilder
import java.text.SimpleDateFormat
import java.util.*
import javax.smartcardio.TerminalFactory
import kotlin.system.exitProcess


fun String.toRDFLiteral() = ResourceFactory.createStringLiteral(this)!!
fun String.toRDFLiteral(type: RDFDatatype) = ResourceFactory.createTypedLiteral(this, type)!!
fun Int.toRDFLiteral() = ResourceFactory.createTypedLiteral(this)
fun Double.toRDFLiteral() = ResourceFactory.createTypedLiteral(this)
fun Date.toRDFDate() = this.toRDFDate(SimpleDateFormat("yyyy-MM-dd"))
fun Date.toRDFDate(format: SimpleDateFormat) = this.toRDFDate(format, XSDDatatype.XSDdate)
fun Date.toRDFDate(format: SimpleDateFormat, dt: RDFDatatype) = format.format(this).toRDFLiteral(dt)

// Extension functions / helper classes

class URI(val value: String) {
    operator fun plus(rs: String) = URI(value + rs)
    operator fun plus(rs: URI) = URI(value + rs.value)
    val res
        get() = ResourceFactory.createResource(value)!!
    val prop
        get() = ResourceFactory.createProperty(value)!!
}

fun Resource.toURI() = URI(this.uri)

fun jenaModel(block: Model.() -> Unit): Model = ModelFactory.createDefaultModel().apply(block)
fun Model.addIfNotExist(stmt: Statement) {
    if (!this.contains(stmt)) this.add(stmt)
}

fun Model.addIfNotExist(s: Resource, p: Property, o: RDFNode) =
    this.addIfNotExist(ResourceFactory.createStatement(s, p, o))


class ParsedArgs(parser: ArgParser) {
    val filename by parser.positional("The JSON file containing the WHO data")
}


exitProcess(main(ArgParser(args).parseInto(::ParsedArgs)))

fun printInfo(what: Any?) = System.err.println(what)

fun main(args: ParsedArgs): Int {
    val filename: String = args.filename
    try {
        File(filename)
        val model = jenaModel {
            read(filename)
        }
        printInfo("Model read from file $filename, ready to query:")
        while(true){
            consoleLoop(model)
        }
    } catch (_: Exception) {
        print("can't read file from ${args.filename}")
        return 0
    }
}

fun consoleLoop(model: Model) {
    val terminal = TerminalBuilder.terminal()
    val reader = LineReaderBuilder.builder().terminal(terminal).build();
    val queryStr = StringBuilder()
    while (true) {
        try {
            val prompt = "sparql>"
            queryStr.append(reader.readLine(prompt))
        } catch (e: UserInterruptException) {
            break
        } catch (e: EndOfFileException) {
            break
        }
    }
    val qExec = QueryExecutionFactory.create(queryStr.toString(), model)
    val query = qExec.query
    when {
        query.isAskType -> println(qExec.execAsk())
        query.isConstructType -> qExec.execConstruct().write(System.out, "TURTLE")
        query.isDescribeType -> qExec.execDescribe().write(System.out, "TURTLE")
        query.isSelectType -> println(ResultSetFormatter.asText(qExec.execSelect()))
    }

}
