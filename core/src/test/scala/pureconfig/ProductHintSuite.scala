package pureconfig

import com.typesafe.config.{ ConfigFactory, ConfigObject }
import pureconfig.error.{ KeyNotFound, UnknownKey }
import pureconfig.syntax._

import scala.collection.JavaConverters._

class ProductHintSuite extends BaseSuite {

  behavior of "ProductHint"

  case class ConfWithCamelCaseInner(thisIsAnInt: Int, thisIsAnotherInt: Int)
  case class ConfWithCamelCase(camelCaseInt: Int, camelCaseString: String, camelCaseConf: ConfWithCamelCaseInner)

  val confWithCamelCase = ConfWithCamelCase(1, "foobar", ConfWithCamelCaseInner(2, 3))

  /** return all the keys in a `ConfigObject` */
  def allKeys(configObject: ConfigObject): Set[String] = {
    configObject.toConfig().entrySet().asScala.flatMap(_.getKey.split('.')).toSet
  }

  it should "read kebab case config keys to camel case fields by default" in {

    val conf = ConfigFactory.parseString("""{
      camel-case-int = 1
      camel-case-string = "bar"
      camel-case-conf {
        this-is-an-int = 3
        this-is-another-int = 10
      }
    }""")

    conf.to[ConfWithCamelCase] shouldBe Right(ConfWithCamelCase(1, "bar", ConfWithCamelCaseInner(3, 10)))
  }

  it should "write kebab case config keys from camel case fields by default" in {
    val conf = confWithCamelCase.toConfig.asInstanceOf[ConfigObject]
    allKeys(conf) should contain theSameElementsAs Seq(
      "camel-case-int",
      "camel-case-string",
      "camel-case-conf",
      "this-is-an-int",
      "this-is-another-int")
  }

  it should "allow customizing the field mapping through a product hint" in {
    val conf = ConfigFactory.parseString("""{
        A = 2
        B = "two"
      }""").root()

    case class SampleConf(a: Int, b: String)
    ConfigConvert[SampleConf].from(conf).left.value.toList should contain theSameElementsAs Seq(KeyNotFound("a", None), KeyNotFound("b", None))

    implicit val productHint = ProductHint[SampleConf](ConfigFieldMapping(_.toUpperCase))
    ConfigConvert[SampleConf].from(conf) shouldBe Right(SampleConf(2, "two"))
  }

  it should "read camel case config keys to camel case fields when configured to do so" in {

    implicit def productHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

    val conf = ConfigFactory.parseString("""{
      camelCaseInt = 1
      camelCaseString = "bar"
      camelCaseConf {
        thisIsAnInt = 3
        thisIsAnotherInt = 10
      }
    }""")

    conf.to[ConfWithCamelCase] shouldBe Right(ConfWithCamelCase(1, "bar", ConfWithCamelCaseInner(3, 10)))
  }

  it should "write camel case config keys to camel case fields when configured to do so" in {
    implicit def productHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

    val conf = confWithCamelCase.toConfig.asInstanceOf[ConfigObject]
    allKeys(conf) should contain theSameElementsAs Seq(
      "camelCaseInt",
      "camelCaseString",
      "camelCaseConf",
      "thisIsAnInt",
      "thisIsAnotherInt")
  }

  it should "read pascal case config keys to pascal case fields when configured to do so" in {

    implicit def productHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, PascalCase))

    val conf = ConfigFactory.parseString("""{
      CamelCaseInt = 1
      CamelCaseString = "bar"
      CamelCaseConf {
        ThisIsAnInt = 3
        ThisIsAnotherInt = 10
      }
    }""")

    conf.to[ConfWithCamelCase] shouldBe Right(ConfWithCamelCase(1, "bar", ConfWithCamelCaseInner(3, 10)))
  }

  it should "write pascal case config keys to pascal case fields when configured to do so" in {
    implicit def productHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, PascalCase))

    val conf = ConfWithCamelCase(1, "foobar", ConfWithCamelCaseInner(2, 3)).toConfig.asInstanceOf[ConfigObject]
    allKeys(conf) should contain theSameElementsAs Seq(
      "CamelCaseInt",
      "CamelCaseString",
      "CamelCaseConf",
      "ThisIsAnInt",
      "ThisIsAnotherInt")
  }

  it should "allow customizing the field mapping only for specific types" in {

    implicit val productHint = ProductHint[ConfWithCamelCase](ConfigFieldMapping(CamelCase, CamelCase))

    val conf = ConfigFactory.parseString("""{
      camelCaseInt = 1
      camelCaseString = "bar"
      camelCaseConf {
        this-is-an-int = 3
        this-is-another-int = 10
      }
    }""")

    conf.to[ConfWithCamelCase] shouldBe Right(ConfWithCamelCase(1, "bar", ConfWithCamelCaseInner(3, 10)))
  }

  it should "disallow unknown keys if specified through a product hint" in {

    case class Conf1(a: Int)
    case class Conf2(a: Int)

    implicit val productHint = ProductHint[Conf2](allowUnknownKeys = false)

    val conf = ConfigFactory.parseString("""{
      conf {
        a = 1
        b = 2
      }
    }""")

    conf.getConfig("conf").to[Conf1] shouldBe Right(Conf1(1))
    val failures = conf.getConfig("conf").to[Conf2].left.value.toList
    failures should have size 1
    failures.head shouldBe a[UnknownKey]
  }

  it should "not use default arguments if specified through a product hint" in {
    case class InnerConf(e: Int, g: Int)
    case class Conf(a: Int, b: String = "default", c: Int = 42, d: InnerConf = InnerConf(43, 44))

    implicit val productHint = ProductHint[Conf](useDefaultArgs = false)

    val conf1 = ConfigFactory.parseMap(Map("a" -> 2).asJava)
    loadConfig[Conf](conf1).left.value.toList should contain theSameElementsAs Seq(KeyNotFound("b", None), KeyNotFound("c", None), KeyNotFound("d", None))
  }
}
