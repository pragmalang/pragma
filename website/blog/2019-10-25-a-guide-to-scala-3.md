---
title: A Guide to Scala 3
author: Muhammad Tabaza
authorTitle: CTO of Pragma
authorImageURL: https://avatars1.githubusercontent.com/u/23503983?s=460&u=9f959a04b620b6ff0f1aa226a19ddf59e6d52517&v=4
authorURL: https://github.com/Tabzz98
authorTwitter: Tabz_98
tags: [functional-programming, scala, beginner]
---

![Scala3](/img/scala-3.jpeg)

Scala 3 comes with many amazing new features. This article attempts to explain the most notable ones, so it is by no means comprehensive. It is, however, a very good introduction to these concepts for beginner to intermediate-level Scala programmers.<!--truncate-->

At the time of this writing, Scala 3 isn’t actually officially released. However, all the features that would be in Scala 3 are available in [Dotty](http://dotty.epfl.ch/), so we’ll be using it instead.

The examples in this article are on [GitHub](https://github.com/Tabzz98/guide-to-scala3-examples), and in order to run them, you can clone the repository and run `sbt console`:

```bash
git clone https://github.com/Tabzz98/guide-to-scala3-examples.git
cd guide-to-scala3-examples/
sbt console
```

:::note
If you use Visual Studio Code, and you have it on the PATH (i.e. the code command is available globally,) you can run sbt launchIDE to get a better editing experience.
:::

## Intersection Types

Consider the following definitions:

```scala
trait A  {
  val a: String
}
trait B {
  val b: Int
}
```

If we think of types in terms of sets, a type would be a collection of elements that satisfy certain properties. For an element to be a member of type `A`, it has to have the property `a` of type `String`. The same goes for elements of type `B`.

Since types are just sets, what would the *intersection* of `A` and `B` be? Well, it’s the set of elements that satisfy the properties of both `A` and `B`. We can denote such a type as `A & B` (the [intersection type](https://dotty.epfl.ch/docs/reference/new-types/intersection-types.html) of `A` and `B`,) and we can use it to specify data types such as the type of the argument c in:

```scala
def f(c: A & B) = c.a + " & " + c.b
```

We can create an instance of `A & B` by creating a subtype of both `A` and `B`:

```scala
case class C(a: String, b: Int)
  extends A with B
```

Which can be passed to `f`:

```scala
scala> f(C("Some String", 42))
val res0: String = Some String & 42
```

Intersection types are actually the equivalent of compound types, so both `A & B` and `A with B` are the same type. However, the `with` syntax will be deprecated in future versions of Scala.

## Union Types

In Scala, we can express that a value can be either of type `A` or of type `B` as `Either[A, B]`. Consider this example:

```scala
val a = new A { val a = "Some String" }
val b = new B { val b = 42 }

type E = Either[A, B]
val l: E = Left(a)
val r: E = Right(b)
```

The `Either` type has two variants: `Left`, and `Right`. In the case of `E`, `Left` represents the variant that carries a value of type `A`, while `Right` carries a value of type `B` (notice the order of the type arguments passed to `Either`.)

Let’s say that we want to define a function `g` of `E`. In order to deal safely with values of `E`, we would need to do some pattern matching:

```scala
def g(e: E): String = e match {
  case Left(a) => s"String value a: ${a.a}"
  case Right(b) => s"Int value b: ${b.b}"
}
```

This is great! We can safely express values that belong to one of two types. But there are a few problems with `Either`:
* It’s not commutative (`Either[A, B]` is not `Either[B, A]`)
* It sucks for working with values that can be of three or more types (I mean, `Either[A, Either[B, Either[Float, Boolean]]]`? This is a nightmare!)

While still thinking of types as sets, let’s think of `Either[A, B]` as the *union* of the sets `A` and `B` (the set containing all elements of both `A` and `B`.) This union can be expressed as `A | B`:

```scala
type U = A | B
```

[Union types](http://dotty.epfl.ch/docs/reference/new-types/union-types.html) solve the two problems listed above, and they’re actually more pleasant to look at and deal with. Consider the definition of `h`:

```scala
def h(v: A | Double | B | Boolean): String = v match {
  case a: A => s"String value a: ${a.a}"
  case d: Double => s"Double value d: $d"
  case b: B => s"Int value b: ${b.b}"
  case bool: Boolean => s"Boolean value bool: $bool"
}
```

Which can be applied with simple arguments (no need to wrap in `Left` or `Right`.) For example:

```scala
scala> h(true)
val res0: String = Boolean value bool: true

scala> h(a)
val res1: String = String value a: Some String

scala> h(42.22)
val res2: String = Double value d: 42.22
```

## Enums

[Enums](http://dotty.epfl.ch/docs/reference/enums/enums.html) are definitions of types’ values by name. They’re useful when a value of a particular type can be one of a well-defined finite set of elements. For example, the definition of WeekDay:

```scala
enum WeekDay {
  case Sunday, Monday, Tuesday, Wednesday,
       Thursday, Friday, Saturday
}
```

Enum values can be parameterized in order to implement *algebraic data types* (*products*, to be specific.) In Scala 2.x, we encode [ADTs](https://nrinaudo.github.io/scala-best-practices/definitions/adt.html) as case classes (or tuples,) and sealed traits. Let’s consider a definition of logical expressions:

```scala
sealed trait VerboseLogicalExpression {
  import VerboseLogicalExpression._
  def eval: Boolean = this match {
    case ConstFactor(c) => c
    ase NotFactor(c) => !c
    case Term(l, Some(r)) => l.eval && r.eval
    case Term(l, None) => l.eval
    case Expr(l, Some(r)) => l.eval || r.eval
    case Expr(l, None) => l.eval
  }
}
object VerboseLogicalExpression {
  sealed trait Factor extends VerboseLogicalExpression
  case class ConstFactor(value: Boolean) extends Factor
  case class NotFactor(value: Boolean) extends Factor
  case class Term(left: Factor, right: Option[Factor])
    extends VerboseLogicalExpression
  case class Expr(left: Term, right: Option[Term])
    extends VerboseLogicalExpression
}
```

Now we can evaluate the expression:

```scala
scala> import VerboseLogicalExpression._

scala> Expr(Term(ConstFactor(true), Some(NotFactor(false))), Some(Term(ConstFactor(false), None))).eval
val res0: Boolean = true
```

Which is just `true ∧ ¬false ∨ true`. Notice how we’ve had to define a `case class` for each variant of `VerboseLogicalExpression`, and made it extend the `trait`. We also did the same with `Factor` just to get the behavior of `ConstFactor | NotFactor`.

Using enums, we can define logical expressions as:

```scala
enum LogicalExpression {
  case ConstFactor(value: Boolean)
  case NotFactor(value: Boolean)
  case Term(
    left: ConstFactor | NotFactor,
    right: Option[ConstFactor | NotFactor]
  )
  case Expr(left: Term, right: Option[Term])
  def eval: Boolean = this match {
    case ConstFactor(c) => c
    case NotFactor(c) => !c
    case Term(l, Some(r)) => l.eval && r.eval
    case Term(l, None) => l.eval
    case Expr(l, Some(r)) => l.eval || r.eval
    case Expr(l, None) => l.eval
  }
}
```

Much cleaner! Note that using the `apply` method of an `enum`’s variant would return a value of the enum type, not the specific case type. We can use `new` to use the constructor of the specific type. So we would define a value of type `LogicalExpression.Expr` as:

```scala
import LogicalExpression._

val expr = new Expr(
  new Term(new ConstFactor(true), Some(new NotFactor(false))),
  Some(new Term(new ConstFactor(false), None))
)
```

## Givens

[Givens](https://dotty.epfl.ch/docs/reference/contextual/motivation.html) are definitions that can be used implicitly. In many ways, they are a more refined version of Scala 2.x’s implicits. Consider this example:

```scala
trait Add[T] {
  def add(x: T, y: T): T
}

given Add[C] {
  def add(x: C, y: C) = C(x.a + y.a, x.b + y.b)
}
```

Here, we define a [*given instance*](https://dotty.epfl.ch/docs/reference/contextual/delegates.html) of an `Add[C]`. This is a more straightforward way of implementing [*type classes*](https://scalac.io/typeclasses-in-scala/).

:::note
Givens do not need to have names, since their type is all that matters in most cases.
:::

We can define functions that have [given parameters](https://dotty.epfl.ch/docs/reference/contextual/given-clauses.html) to summon any defined given instance:

```scala
def zipAdd[T](xs: List[T], ys: List[T])(given a: Add[T]): List[T] =
  xs.zip(ys).map(a.add)

scala> zipAdd(List(C("Welcome ", 18), C("Scala ", 0)), 
     |        List(C("to", 22), C("3", 2)))
val res0: List[C] = List(C(Welcome to,40), C(Scala 3,2))
```

Similarly, we can define given value aliases:

```scala
given Int = 42

def addMagicNumber(x: Int)(given magicNumber: Int) = x + magicNumber

scala> addMagicNumber(2)
val res0: Int = 44
```

Implicit conversions are replaced with the definition of a given instance of `Conversion[T, U]`. For example, we can define an implicit conversion from `String` to `A` as:

```scala
given Conversion[String, A] {
  def apply(s: String) = new A { val a = s }
}

scala> h(a) // `a` is converted from A to String
val res0: String = String value a: Some String

scala> :type h
A | Double | B | Boolean => String
```

Using wildcard syntax in imports will not import any given definitions. Instead, you must use `.given` syntax, or import the given definitions by name. See [here](https://dotty.epfl.ch/docs/reference/contextual/import-delegate.html) for more information about imports.

## Extension Methods

Scala 3 offers a really simple way to add new methods to existing types. Consider the following example:

```scala
def (x: T) +[T] (y: T)(given a: Add[T]) = a.add(x, y)
```

This definition (when in scope,) adds the `+` method to any type `T` for which a given instance of `Add[T]` is defined, such as the type `C`.

```scala
scala> C("Yay ", 2) + C("Simplicity!", 40)
val res0: C = C(Yay Simplicity!,42)
```

In 2.x, you would have to define an [implicit class](https://docs.scala-lang.org/overviews/core/implicit-classes.html) with the extension method defined on it to achieve this. This is a much simpler approach.

## Conclusion

There still are many features not covered here: [typeclass derivation](https://dotty.epfl.ch/docs/reference/contextual/derivation.html), [match types](https://dotty.epfl.ch/docs/reference/new-types/match-types.html), and an [entirely new macro system](https://dotty.epfl.ch/docs/reference/metaprogramming/toc.html), just to name a few. This article would need to be much longer than this in order to cover all of them. Luckily, the documentation offers a great introduction to these concepts, even though it might be lacking in some regards, but that’ll surely get better with the official release of Scala 3.
