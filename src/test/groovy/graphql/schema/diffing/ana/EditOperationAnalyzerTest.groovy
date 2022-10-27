package graphql.schema.diffing.ana

import graphql.TestUtil
import graphql.schema.diffing.SchemaDiffing
import graphql.schema.idl.errors.DirectiveMissingNonNullArgumentError
import spock.lang.Specification

import static graphql.schema.diffing.ana.SchemaDifference.*

class EditOperationAnalyzerTest extends Specification {

    def "object renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        schema {
         query: MyQuery 
        }
        type MyQuery {
            foo: String
        }
         
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] === changes.objectChanges["MyQuery"]
        changes.objectChanges["Query"] instanceof ObjectModification
        (changes.objectChanges["Query"] as ObjectModification).oldName == "Query"
        (changes.objectChanges["Query"] as ObjectModification).newName == "MyQuery"
    }

    def "interface renamed"() {
        given:
        def oldSdl = '''
        type Query implements I {
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements IRenamed {
            foo: String
        }
        interface IRenamed {
            foo: String
        }
         
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges["I"] === changes.interfaceChanges["IRenamed"]
        changes.interfaceChanges["I"] instanceof InterfaceModification
        (changes.interfaceChanges["I"] as InterfaceModification).oldName == "I"
        (changes.interfaceChanges["I"] as InterfaceModification).newName == "IRenamed"
    }

    def "interface removed from object"() {
        given:
        def oldSdl = '''
        type Query implements I {
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query{
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def implementationDeletions = (changes.objectChanges["Query"] as ObjectModification).getDetails(ObjectInterfaceImplementationDeletion)
        implementationDeletions[0].name == "I"
    }

    def "interface removed from interface"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        interface FooI {
            foo: String
        }
        interface Foo implements FooI {
            foo: String
        }
        type FooImpl implements Foo & FooI {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface Foo {
            foo: String
        }
        type FooImpl implements Foo {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges["Foo"] instanceof InterfaceModification
        def implementationDeletions = (changes.interfaceChanges["Foo"] as InterfaceModification).getDetails(InterfaceInterfaceImplementationDeletion)
        implementationDeletions[0].name == "FooI"
    }

    def "field renamed"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello2: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def fieldRenames = objectModification.getDetails(ObjectFieldRename.class)
        fieldRenames[0].oldName == "hello"
        fieldRenames[0].newName == "hello2"
    }

    def "union added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B 
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionAddition
        (changes.unionChanges["U"] as UnionAddition).name == "U"
    }

    def "union deleted"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionDeletion
        (changes.unionChanges["U"] as UnionDeletion).name == "U"
    }

    def "union renamed"() {
        given:
        def oldSdl = '''
        type Query {
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            u: X 
        }
        union X = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["X"] === changes.unionChanges["U"]
        changes.unionChanges["U"] instanceof UnionModification
        (changes.unionChanges["U"] as UnionModification).oldName == "U"
        (changes.unionChanges["U"] as UnionModification).newName == "X"
    }

    def "union renamed and member removed"() {
        given:
        def oldSdl = '''
        type Query {
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            u: X 
        }
        union X = A 
        type A {
            foo: String
        } 
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionModification
        def unionDiff = changes.unionChanges["U"] as UnionModification
        unionDiff.oldName == "U"
        unionDiff.newName == "X"
        unionDiff.getDetails(UnionMemberDeletion)[0].name == "B"
    }

    def "union renamed and member added"() {
        given:
        def oldSdl = '''
        type Query {
            u: U 
        }
        union U = A 
        type A {
            foo: String
        } 

        '''
        def newSdl = '''
        type Query {
            u: X
        }
        union X = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionModification
        def unionDiff = changes.unionChanges["U"] as UnionModification
        unionDiff.oldName == "U"
        unionDiff.newName == "X"
        unionDiff.getDetails(UnionMemberAddition)[0].name == "B"
    }

    def "union member added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B | C
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        type C {
            foo: String
        } 

        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionModification
        def unionModification = changes.unionChanges["U"] as UnionModification
        unionModification.getDetails(UnionMemberAddition)[0].name == "C"
    }

    def "union member deleted"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A | B
        type A {
            foo: String
        } 
        type B {
            foo: String
        } 
        '''
        def newSdl = '''
        type Query {
            hello: String
            u: U
        }
        union U = A 
        type A {
            foo: String
        } 
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.unionChanges["U"] instanceof UnionModification
        def unionModification = changes.unionChanges["U"] as UnionModification
        unionModification.getDetails(UnionMemberDeletion)[0].name == "B"
    }

    def "field type modified"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def typeModification = objectModification.getDetails(ObjectFieldTypeModification.class)
        typeModification[0].oldType == "String"
        typeModification[0].newType == "String!"
    }

    def "object and interface field argument renamed"() {
        given:
        def oldSdl = '''
        type Query implements I{
            hello(arg: String): String
        }
        interface I {
            hello(arg: String): String
        } 
        '''
        def newSdl = '''
        type Query implements I{
            hello(argRename: String): String
        }
        interface I {
            hello(argRename: String): String
        } 
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def objectArgumentRenamed = objectModification.getDetails(ObjectFieldArgumentRename.class);
        objectArgumentRenamed[0].oldName == "arg"
        objectArgumentRenamed[0].newName == "argRename"
        and:
        changes.interfaceChanges["I"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceChanges["I"] as InterfaceModification
        def interfaceArgumentRenamed = interfaceModification.getDetails(InterfaceFieldArgumentRename.class);
        interfaceArgumentRenamed[0].oldName == "arg"
        interfaceArgumentRenamed[0].newName == "argRename"

    }


    def "object field argument removed"() {
        given:
        def oldSdl = '''
        type Query {
            hello(arg: String): String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def argumentRemoved = objectModification.getDetails(ObjectFieldArgumentDeletion.class);
        argumentRemoved[0].fieldName == "hello"
        argumentRemoved[0].name == "arg"
    }

    def "argument default value modified for Object and Interface"() {
        given:
        def oldSdl = '''
        type Query implements Foo {
            foo(arg: String = "bar"): String
        }
        interface Foo {
            foo(arg: String = "bar"): String
        }
        
        '''
        def newSdl = '''
        type Query implements Foo {
            foo(arg: String = "barChanged"): String
        }
        interface Foo {
            foo(arg: String = "barChanged"): String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)

        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def objDefaultValueModified = objectModification.getDetails(ObjectFieldArgumentDefaultValueModification.class);
        objDefaultValueModified[0].fieldName == "foo"
        objDefaultValueModified[0].argumentName == "arg"
        objDefaultValueModified[0].oldValue == '"bar"'
        objDefaultValueModified[0].newValue == '"barChanged"'
        and:
        changes.interfaceChanges["Foo"] instanceof InterfaceModification
        def interfaceModification = changes.interfaceChanges["Foo"] as InterfaceModification
        def intDefaultValueModified = interfaceModification.getDetails(InterfaceFieldArgumentDefaultValueModification.class);
        intDefaultValueModified[0].fieldName == "foo"
        intDefaultValueModified[0].argumentName == "arg"
        intDefaultValueModified[0].oldValue == '"bar"'
        intDefaultValueModified[0].newValue == '"barChanged"'
    }

    def "field added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            newOne: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Query"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Query"] as ObjectModification
        def fieldAdded = objectModification.getDetails(ObjectFieldAddition)
        fieldAdded[0].name == "newOne"
    }

    def "object added"() {
        given:
        def oldSdl = '''
        type Query {
            hello: String
        }
        '''
        def newSdl = '''
        type Query {
            hello: String
            foo: Foo
        }
        type Foo {
            id: ID
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Foo"] instanceof ObjectAddition
    }

    def "object removed and field type changed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.objectChanges["Foo"] instanceof ObjectDeletion
        (changes.objectChanges["Foo"] as ObjectDeletion).name == "Foo"
        changes.objectChanges["Query"] instanceof ObjectModification
        def queryObjectModification = changes.objectChanges["Query"] as ObjectModification
        queryObjectModification.details.size() == 1
        queryObjectModification.details[0] instanceof ObjectFieldTypeModification
        (queryObjectModification.details[0] as ObjectFieldTypeModification).oldType == "Foo"
        (queryObjectModification.details[0] as ObjectFieldTypeModification).newType == "String"

    }

    def "Interface and Object field type changed completely"() {
        given:
        def oldSdl = '''
        type Query implements I{
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            foo: ID
        }
        interface I {
            foo: ID
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges["I"] instanceof InterfaceModification
        def iModification = changes.interfaceChanges["I"] as InterfaceModification
        def iFieldTypeModifications = iModification.getDetails(InterfaceFieldTypeModification)
        iFieldTypeModifications[0].fieldName == "foo"
        iFieldTypeModifications[0].oldType == "String"
        iFieldTypeModifications[0].newType == "ID"
        and:
        changes.objectChanges["Query"] instanceof ObjectModification
        def oModification = changes.objectChanges["Query"] as ObjectModification
        def oFieldTypeModifications = oModification.getDetails(ObjectFieldTypeModification)
        oFieldTypeModifications[0].fieldName == "foo"
        oFieldTypeModifications[0].oldType == "String"
        oFieldTypeModifications[0].newType == "ID"
    }

    def "Interface and Object field type changed wrapping type"() {
        given:
        def oldSdl = '''
        type Query implements I{
            foo: String
        }
        interface I {
            foo: String
        }
        '''
        def newSdl = '''
        type Query implements I{
            foo: [String!]
        }
        interface I {
            foo: [String!]
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges["I"] instanceof InterfaceModification
        def iModification = changes.interfaceChanges["I"] as InterfaceModification
        def iFieldTypeModifications = iModification.getDetails(InterfaceFieldTypeModification)
        iFieldTypeModifications[0].fieldName == "foo"
        iFieldTypeModifications[0].oldType == "String"
        iFieldTypeModifications[0].newType == "[String!]"
        and:
        changes.objectChanges["Query"] instanceof ObjectModification
        def oModification = changes.objectChanges["Query"] as ObjectModification
        def oFieldTypeModifications = oModification.getDetails(ObjectFieldTypeModification)
        oFieldTypeModifications[0].fieldName == "foo"
        oFieldTypeModifications[0].oldType == "String"
        oFieldTypeModifications[0].newType == "[String!]"


    }

    def "new Interface introduced"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        type Foo {
          id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 1
        changes.interfaceChanges["Node"] instanceof InterfaceAddition
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition.class)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "Node"
    }

    def "Object and Interface added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 1
        changes.interfaceChanges["Node"] instanceof InterfaceAddition
        changes.objectChanges.size() == 2
        changes.objectChanges["Foo"] instanceof ObjectAddition
        changes.objectChanges["Query"] instanceof ObjectModification
        (changes.objectChanges["Query"] as ObjectModification).getDetails()[0] instanceof ObjectFieldTypeModification
    }

    def "interfaced renamed"() {
        given:
        def oldSdl = '''
        type Query {
          foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface Node2 {
            id: ID!
        }
        type Foo implements Node2{
            id: ID!
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 2
        changes.interfaceChanges["Node"] === changes.interfaceChanges["Node2"]
        changes.interfaceChanges["Node2"] instanceof InterfaceModification
    }

    def "interfaced renamed and another interface added to it"() {
        given:
        def oldSdl = '''
        type Query {
          foo: Foo
        }
        type Foo implements Node{
            id: ID!
        }
        interface Node {
            id: ID!
        }
        '''
        def newSdl = '''
        type Query {
            foo: Foo
        }
        interface NewI {
            hello: String
        }
        interface Node2 {
            id: ID!
        }
        type Foo implements Node2 & NewI{
            id: ID!
            hello: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.interfaceChanges.size() == 3
        changes.interfaceChanges["Node"] == changes.interfaceChanges["Node2"]
        changes.interfaceChanges["Node2"] instanceof InterfaceModification
        changes.interfaceChanges["NewI"] instanceof InterfaceAddition
        changes.objectChanges.size() == 1
        changes.objectChanges["Foo"] instanceof ObjectModification
        def objectModification = changes.objectChanges["Foo"] as ObjectModification
        def addedInterfaceDetails = objectModification.getDetails(ObjectInterfaceImplementationAddition)
        addedInterfaceDetails.size() == 1
        addedInterfaceDetails[0].name == "NewI"

    }

    def "enum renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        enum E {
            A, B
        }
        '''
        def newSdl = '''
        type Query {
            foo: ERenamed
        }
        enum ERenamed {
            A, B
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.enumChanges["E"] === changes.enumChanges["ERenamed"]
        def modification = changes.enumChanges["E"] as EnumModification
        modification.oldName == "E"
        modification.newName == "ERenamed"

    }

    def "enum added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: E
        }
        enum E {
            A, B
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.enumChanges["E"] instanceof EnumAddition
        (changes.enumChanges["E"] as EnumAddition).getName() == "E"
    }

    def "enum deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: E
        }
        enum E {
            A, B
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.enumChanges["E"] instanceof EnumDeletion
        (changes.enumChanges["E"] as EnumDeletion).getName() == "E"
    }


    def "enum value added"() {
        given:
        def oldSdl = '''
        type Query {
            e: E
        }
        enum E {
            A
        }
        '''
        def newSdl = '''
        type Query {
            e: E
        }
        enum E {
            A, B
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.enumChanges["E"] instanceof EnumModification
        def enumModification = changes.enumChanges["E"] as EnumModification
        enumModification.getDetails(EnumValueAddition)[0].name == "B"
    }

    def "enum value deleted"() {
        given:
        def oldSdl = '''
        type Query {
            e: E
        }
        enum E {
            A,B
        }
        '''
        def newSdl = '''
        type Query {
            e: E
        }
        enum E {
            A
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.enumChanges["E"] instanceof EnumModification
        def enumModification = changes.enumChanges["E"] as EnumModification
        enumModification.getDetails(EnumValueDeletion)[0].name == "B"
    }

    def "scalar added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: E
        }
        scalar E
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.scalarChanges["E"] instanceof ScalarAddition
        (changes.scalarChanges["E"] as ScalarAddition).getName() == "E"
    }

    def "scalar deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: E
        }
        scalar E
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.scalarChanges["E"] instanceof ScalarDeletion
        (changes.scalarChanges["E"] as ScalarDeletion).getName() == "E"
    }

    def "scalar renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: Foo
        }
        scalar Foo
        '''
        def newSdl = '''
        type Query {
            foo: Bar
        }
        scalar Bar
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.scalarChanges["Foo"] === changes.scalarChanges["Bar"]
        def modification = changes.scalarChanges["Foo"] as ScalarModification
        modification.oldName == "Foo"
        modification.newName == "Bar"
    }

    def "input object added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.inputObjectChanges["I"] instanceof InputObjectAddition
        (changes.inputObjectChanges["I"] as InputObjectAddition).getName() == "I"
    }

    def "input object deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.inputObjectChanges["I"] instanceof InputObjectDeletion
        (changes.inputObjectChanges["I"] as InputObjectDeletion).getName() == "I"
    }

    def "input object renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo(arg: I): String 
        }
        input I {
            bar: String
        }
        '''
        def newSdl = '''
        type Query {
            foo(arg: IRenamed): String 
        }
        input IRenamed {
            bar: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.inputObjectChanges["I"] === changes.inputObjectChanges["IRenamed"]
        def modification = changes.inputObjectChanges["I"] as InputObjectModification
        modification.oldName == "I"
        modification.newName == "IRenamed"
    }



    def "directive added"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.directiveChanges["d"] instanceof DirectiveAddition
        (changes.directiveChanges["d"] as DirectiveAddition).getName() == "d"
    }

    def "directive deleted"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.directiveChanges["d"] instanceof DirectiveDeletion
        (changes.directiveChanges["d"] as DirectiveDeletion).getName() == "d"
    }

    def "directive renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @dRenamed on FIELD
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.directiveChanges["d"] === changes.directiveChanges["dRenamed"]
        def modification = changes.directiveChanges["d"] as DirectiveModification
        modification.oldName == "d"
        modification.newName == "dRenamed"
    }

    def "directive argument renamed"() {
        given:
        def oldSdl = '''
        type Query {
            foo: String
        }
        directive @d(foo: String) on FIELD
        '''
        def newSdl = '''
        type Query {
            foo: String
        }
        directive @d(bar:String) on FIELD
        '''
        when:
        def changes = changes(oldSdl, newSdl)
        then:
        changes.directiveChanges["d"] instanceof DirectiveModification
        def renames = (changes.directiveChanges["d"] as DirectiveModification).getDetails(DirectiveArgumentRename)
        renames[0].oldName == "foo"
        renames[0].newName == "bar"


    }


    EditOperationAnalysisResult changes(
            String oldSdl,
            String newSdl
    ) {
        def oldSchema = TestUtil.schema(oldSdl)
        def newSchema = TestUtil.schema(newSdl)
        def changes = new SchemaDiffing().diffAndAnalyze(oldSchema, newSchema)
        return changes
    }
}